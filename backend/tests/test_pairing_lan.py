from pathlib import Path
from tempfile import TemporaryDirectory
import unittest
from unittest.mock import patch

import httpx

from greedycat.api.pairing import create_pairing_app
from greedycat.security.addresses import private_address
from greedycat.security.identity import initialize, validate_bind
from greedycat.security.pairing import PairingStore


class AddressTests(unittest.TestCase):
    def test_only_numeric_loopback_or_rfc1918_ipv4(self):
        for value in ("127.0.0.1", "192.168.1.2", "10.0.0.1", "172.31.1.2"):
            self.assertEqual(value, private_address(value))
        for value in ("0.0.0.0", "8.8.8.8", "169.254.1.2", "192.0.2.10", "172.32.0.1", "localhost", "::1", "192.168.01.2"):
            with self.assertRaises(ValueError):
                private_address(value)

    def test_bind_requires_certificate_match_and_explicit_lan_flag(self):
        from greedycat.pairing_server import main
        with TemporaryDirectory() as name:
            directory = Path(name)
            initialize(directory, "192.168.1.2")
            validate_bind(directory, "192.168.1.2")
            validate_bind(directory, "127.0.0.1")
            with self.assertRaises(ValueError):
                validate_bind(directory, "192.168.1.3")
            with patch("sys.argv", ["pairing", "--directory", name, "serve", "--bind", "192.168.1.2"]), patch("sys.stderr"):
                with self.assertRaises(SystemExit) as caught:
                    main()
            self.assertEqual(2, caught.exception.code)


class LanBoundaryTests(unittest.IsolatedAsyncioTestCase):
    async def test_other_explicit_loopback_bind_has_matching_host_boundary(self):
        with TemporaryDirectory() as name:
            app = create_pairing_app(PairingStore(Path(name) / "pairing.sqlite3"), "127.0.0.2")
            async with httpx.AsyncClient(transport=httpx.ASGITransport(app=app, client=("127.0.0.1", 1)),
                                        base_url="https://127.0.0.2:8767") as client:
                self.assertEqual(401, (await client.get("/api/pairing/v1/session")).status_code)
                self.assertEqual(403, (await client.get("/api/pairing/v1/session", headers={"Host": "127.0.0.1:8767"})).status_code)

    async def test_private_listener_still_requires_authentication_and_exact_host(self):
        with TemporaryDirectory() as name:
            store = PairingStore(Path(name) / "pairing.sqlite3")
            app = create_pairing_app(store, "192.168.1.2")
            async def request(peer="192.168.1.3", headers=None, scheme="https"):
                async with httpx.AsyncClient(transport=httpx.ASGITransport(app=app, client=(peer, 1)),
                                            base_url=f"{scheme}://192.168.1.2:8767") as client:
                    return await client.get("/api/pairing/v1/session", headers=headers)
            self.assertEqual(401, (await request()).status_code)
            self.assertEqual(403, (await request(peer="8.8.8.8")).status_code)
            self.assertEqual(403, (await request(headers={"Host": "192.168.1.4:8767"})).status_code)
            self.assertEqual(403, (await request(headers={"Origin": "https://192.168.1.2:8767"})).status_code)
            self.assertEqual(403, (await request(scheme="http", headers={"X-Forwarded-Proto": "https"})).status_code)
