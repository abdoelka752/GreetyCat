from concurrent.futures import ThreadPoolExecutor
from contextlib import closing
from pathlib import Path
import socket
import sqlite3
import ssl
import subprocess
import sys
from tempfile import TemporaryDirectory
import time
import unittest
from unittest.mock import patch

import httpx

from greedycat.api.pairing import create_pairing_app
from greedycat.security.identity import fingerprint, initialize
from greedycat.security.pairing import PairingDenied, PairingStore


class StoreTests(unittest.TestCase):
    def setUp(self):
        self.temp = TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.path = Path(self.temp.name) / "pairing.sqlite3"
        self.now = 1000.0
        self.store = PairingStore(self.path, clock=lambda: self.now)

    def paired(self):
        return self.store.exchange(self.store.invite()["invitation"])

    def test_exchange_is_one_use_and_new_invitation_replaces_previous(self):
        first = self.store.invite()["invitation"]
        second = self.store.invite()["invitation"]
        with self.assertRaises(PairingDenied):
            self.store.exchange(first)
        self.store.exchange(second)
        with self.assertRaises(PairingDenied):
            self.store.exchange(second)

    def test_invalid_invitation_does_not_consume_valid_invitation(self):
        secret = self.store.invite()["invitation"]
        with self.assertRaises(PairingDenied):
            self.store.exchange("x" * 43)
        self.store.exchange(secret)

    def test_invitation_expires_at_exact_boundary(self):
        invite = self.store.invite()
        self.now = invite["expires_at"]
        with self.assertRaises(PairingDenied):
            self.store.exchange(invite["invitation"])

    def test_cancel_invitation(self):
        invite = self.store.invite()
        self.store.cancel_invitation()
        with self.assertRaises(PairingDenied):
            self.store.exchange(invite["invitation"])

    def test_session_survives_restart_and_only_hashes_are_stored(self):
        invite = self.store.invite()
        credential = self.store.exchange(invite["invitation"])
        restarted = PairingStore(self.path, clock=lambda: self.now)
        self.assertEqual(credential["pairing_id"], restarted.authenticate(credential["token"])["pairing_id"])
        with closing(sqlite3.connect(self.path)) as db:
            dump = "\n".join(db.iterdump())
        self.assertNotIn(credential["token"], dump)
        self.assertNotIn(invite["invitation"], dump)
        self.assertNotIn(credential["token"].encode(), self.path.read_bytes())

    def test_expiry_cannot_be_resurrected_by_clock_rollback(self):
        credential = self.paired()
        self.now = credential["expires_at"]
        with self.assertRaises(PairingDenied):
            self.store.authenticate(credential["token"])
        self.now -= 10
        with self.assertRaises(PairingDenied):
            self.store.authenticate(credential["token"])
        self.now = credential["expires_at"] + 1
        self.assertEqual([], self.store.list_sessions())

    def test_revoke_by_operator_and_self_is_persistent(self):
        for mode in ("operator", "self"):
            credential = self.paired()
            if mode == "operator":
                self.assertTrue(self.store.revoke(credential["pairing_id"]))
                self.assertFalse(self.store.revoke(credential["pairing_id"]))
            else:
                self.assertEqual("REVOKED", self.store.authenticate(credential["token"], revoke=True)["status"])
            with self.assertRaises(PairingDenied):
                PairingStore(self.path, clock=lambda: self.now).authenticate(credential["token"])

    def test_no_cross_session_authority_and_capacity_is_bounded(self):
        self.store.MAX_SESSIONS = 2
        a, b = self.paired(), self.paired()
        with self.assertRaises(PairingDenied):
            self.paired()
        self.store.authenticate(a["token"], revoke=True)
        self.assertEqual(b["pairing_id"], self.store.authenticate(b["token"])["pairing_id"])
        self.paired()

    def test_concurrent_exchange_has_exactly_one_winner(self):
        invitation = self.store.invite()["invitation"]
        def attempt(_):
            try:
                return self.store.exchange(invitation)
            except PairingDenied:
                return None
        with ThreadPoolExecutor(max_workers=4) as pool:
            results = list(pool.map(attempt, range(4)))
        self.assertEqual(1, sum(result is not None for result in results))

    def test_future_ledger_version_is_rejected_without_rewrite(self):
        with closing(sqlite3.connect(self.path)) as db:
            db.execute("PRAGMA user_version=2")
        with self.assertRaises(ValueError):
            PairingStore(self.path)
        with closing(sqlite3.connect(self.path)) as db:
            self.assertEqual(2, db.execute("PRAGMA user_version").fetchone()[0])


class ApiTests(unittest.IsolatedAsyncioTestCase):
    def setUp(self):
        self.temp = TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.store = PairingStore(Path(self.temp.name) / "pairing.sqlite3")
        self.app = create_pairing_app(self.store)

    async def request(self, path="session", *, method="GET", scheme="https", peer="127.0.0.1", **kwargs):
        async with httpx.AsyncClient(transport=httpx.ASGITransport(app=self.app, client=(peer, 1),
                                      raise_app_exceptions=False), base_url=f"{scheme}://127.0.0.1:8767") as client:
            return await client.request(method, "/api/pairing/v1/" + path, **kwargs)

    async def test_exchange_authenticated_probe_and_self_revoke(self):
        result = await self.request("exchange", method="POST", json=self.store.invite())
        self.assertEqual(422, result.status_code)  # expires_at is not a client field
        invite = self.store.invite()
        result = await self.request("exchange", method="POST", json={"invitation": invite["invitation"]})
        self.assertEqual(200, result.status_code)
        token = result.json()["token"]
        headers = {"Authorization": "Bearer " + token}
        probe = await self.request(headers=headers)
        self.assertEqual("PAIRED", probe.json()["status"])
        self.assertNotIn(token, probe.text)
        self.assertEqual("no-store", probe.headers["cache-control"])
        self.assertEqual(200, (await self.request(method="DELETE", headers=headers)).status_code)
        self.assertEqual(401, (await self.request(headers=headers)).status_code)

    async def test_missing_bad_duplicate_and_query_credentials_denied(self):
        for headers in ({}, {"Authorization": "Bearer " + "x" * 43},
                        {"Authorization": "Basic ignored"},
                        [("Authorization", "Bearer " + "x" * 43)] * 2):
            self.assertEqual(401, (await self.request(headers=headers)).status_code)
        self.assertEqual(401, (await self.request(params={"token": "x" * 43})).status_code)

    async def test_access_boundary_rejects_plaintext_remote_browser_and_forgery(self):
        cases = [dict(scheme="http"), dict(peer="192.0.2.10"),
                 dict(scheme="http", headers={"X-Forwarded-Proto": "https"}),
                 dict(headers={"Host": "attacker.invalid"}),
                 dict(headers={"Origin": "https://127.0.0.1:8767"}),
                 dict(headers={"Sec-Fetch-Site": "same-origin"})]
        for kwargs in cases:
            self.assertEqual(403, (await self.request(**kwargs)).status_code)

    async def test_body_limit_invalid_json_and_no_secret_echo(self):
        self.assertEqual(413, (await self.request("exchange", method="POST", content=b"x" * 4097)).status_code)
        async def chunks():
            for _ in range(5):
                yield b"x" * 1024
        self.assertEqual(413, (await self.request("exchange", method="POST", content=chunks())).status_code)
        for body in (b"{invalid-secret", b'{"invitation":"short-private"}'):
            response = await self.request("exchange", method="POST", content=body,
                                          headers={"Content-Type": "application/json"})
            self.assertEqual(422, response.status_code)
            self.assertNotIn("private", response.text)

    async def test_no_gameplay_diagnostics_or_invitation_creation_over_http(self):
        for route in ("invite", "devices", "rounds", "openapi.json"):
            self.assertEqual(404, (await self.request(route)).status_code)

    async def test_database_errors_are_sanitized(self):
        with patch.object(self.store, "authenticate", side_effect=RuntimeError("private path")):
            response = await self.request(headers={"Authorization": "Bearer " + "x" * 43})
        self.assertEqual(500, response.status_code)
        self.assertNotIn("private", response.text)


class TlsTests(unittest.TestCase):
    def test_real_https_listener_trust_hostname_exchange_and_revocation(self):
        with TemporaryDirectory() as name:
            directory = Path(name)
            initialize(directory)
            original = fingerprint(directory)
            with self.assertRaises(ValueError):
                initialize(directory)
            self.assertEqual(original, fingerprint(directory))
            store = PairingStore(directory / "pairing.sqlite3")
            invitation = store.invite()
            with socket.socket() as reservation:
                reservation.bind(("127.0.0.1", 0))
                port = reservation.getsockname()[1]
            process = subprocess.Popen([sys.executable, "-m", "greedycat.pairing_server",
                                        "--directory", str(directory), "serve", "--port", str(port)],
                                       stdin=subprocess.DEVNULL, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
            try:
                context = ssl.create_default_context(cafile=str(directory / "server.pem"))
                url = f"https://127.0.0.1:{port}"
                with httpx.Client(verify=context, trust_env=False, timeout=2) as client:
                    deadline = time.monotonic() + 15
                    while True:
                        try:
                            response = client.get(url + "/api/pairing/v1/session")
                            break
                        except (httpx.ConnectError, httpx.ConnectTimeout):
                            if time.monotonic() > deadline or process.poll() is not None:
                                self.fail("TLS listener failed to start")
                            time.sleep(0.05)
                    self.assertEqual(401, response.status_code)
                    result = client.post(url + "/api/pairing/v1/exchange",
                                         json={"invitation": invitation["invitation"]})
                    self.assertEqual(200, result.status_code)
                    credential = result.json()
                    headers = {"Authorization": "Bearer " + credential["token"]}
                    self.assertEqual(200, client.get(url + "/api/pairing/v1/session", headers=headers).status_code)
                    self.assertTrue(store.revoke(credential["pairing_id"]))
                    self.assertEqual(401, client.get(url + "/api/pairing/v1/session", headers=headers).status_code)
                with httpx.Client(trust_env=False, timeout=2) as untrusted:
                    with self.assertRaises(httpx.ConnectError):
                        untrusted.get(url + "/api/pairing/v1/session")
                other = directory / "other"
                initialize(other)
                wrong_trust = ssl.create_default_context(cafile=str(other / "server.pem"))
                with httpx.Client(verify=wrong_trust, trust_env=False, timeout=2) as substituted:
                    with self.assertRaises(httpx.ConnectError):
                        substituted.get(url + "/api/pairing/v1/session")
                with socket.create_connection(("127.0.0.1", port), timeout=2) as raw:
                    with self.assertRaises(ssl.SSLCertVerificationError):
                        context.wrap_socket(raw, server_hostname="wrong.invalid")
                # The configured minimum is tested without enabling obsolete TLS locally.
                from greedycat.security.identity import server_context
                self.assertEqual(ssl.TLSVersion.TLSv1_2, server_context(directory).minimum_version)
            finally:
                process.terminate()
                process.wait(timeout=10)

    def test_launcher_rejects_host_override(self):
        from greedycat.pairing_server import main
        with patch("sys.argv", ["pairing", "serve", "--host", "0.0.0.0"]), patch("sys.stderr"):
            with self.assertRaises(SystemExit) as caught:
                main()
        self.assertEqual(2, caught.exception.code)
