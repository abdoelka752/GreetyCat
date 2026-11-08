"""Owned-app pairing acceptance only. No game navigation, capture or data clearing.

Uses the single authorized physical phone. Explicitly run after app-testing consent.
Secrets travel to an app-private temporary fixture through run-as stdin, never argv/logs.
"""

import argparse
from contextlib import contextmanager
import json
import os
from pathlib import Path
import re
import socket
import subprocess
import sys
from tempfile import TemporaryDirectory
from threading import Thread
import time
from contextlib import closing

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "backend"))

import uvicorn
from cryptography import x509
from cryptography.hazmat.primitives import hashes, serialization
from ipaddress import ip_address
from greedycat.api.pairing import create_pairing_app
from greedycat.security.addresses import private_address
from greedycat.security.identity import fingerprint, initialize, server_context
from greedycat.security.pairing import PairingStore


@contextmanager
def listener(directory, bind="127.0.0.1", certificate_address=None):
    initialize(directory, certificate_address or bind)
    if certificate_address is not None:
        # Synthetic negative-control identity: intentionally remove loopback SAN.
        # The production launcher rejects this bind/certificate mismatch.
        cert = x509.load_pem_x509_certificate((directory / "server.pem").read_bytes())
        key = serialization.load_pem_private_key((directory / "server.key").read_bytes(), password=None)
        builder = (x509.CertificateBuilder().subject_name(cert.subject).issuer_name(cert.issuer)
                   .public_key(cert.public_key()).serial_number(cert.serial_number)
                   .not_valid_before(cert.not_valid_before_utc).not_valid_after(cert.not_valid_after_utc))
        for extension in cert.extensions:
            value = x509.SubjectAlternativeName([x509.IPAddress(ip_address(certificate_address))]) if isinstance(extension.value, x509.SubjectAlternativeName) else extension.value
            builder = builder.add_extension(value, extension.critical)
        (directory / "server.pem").write_bytes(builder.sign(key, hashes.SHA256()).public_bytes(serialization.Encoding.PEM))
    store = PairingStore(directory / "pairing.sqlite3")
    with socket.socket() as sock:
        sock.bind((bind, 0))
        port = sock.getsockname()[1]
        config = uvicorn.Config(create_pairing_app(store, bind), log_level="critical", access_log=False,
                                proxy_headers=False, ws="none", ssl_certfile=str(directory / "server.pem"),
                                ssl_keyfile=str(directory / "server.key"), limit_concurrency=16,
                                timeout_keep_alive=1, timeout_graceful_shutdown=5)
        config.load()
        config.ssl = server_context(directory)
        server = uvicorn.Server(config)
        thread = Thread(target=lambda: server.run(sockets=[sock]), daemon=True)
        thread.start()
        try:
            deadline = time.monotonic() + 10
            while not server.started:
                if not thread.is_alive() or time.monotonic() > deadline:
                    raise RuntimeError("Temporary pairing listener did not start.")
                time.sleep(0.05)
            yield port, store
        finally:
            server.should_exit = True
            thread.join(timeout=10)
            if thread.is_alive():
                server.force_exit = True
                thread.join(timeout=10)
            if thread.is_alive():
                raise RuntimeError("Temporary listener failed to stop.")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--lan-address", help="Explicit PC RFC1918 IP, otherwise use owned USB reverse mappings.")
    parser.add_argument("--sync", action="store_true", help="Run the isolated T009 review-sync integration instead of pairing-only acceptance.")
    args = parser.parse_args()
    bind = private_address(args.lan_address) if args.lan_address else "127.0.0.1"
    sdk = Path(os.environ.get("ANDROID_HOME") or os.environ.get("ANDROID_SDK_ROOT") or
               Path(os.environ["LOCALAPPDATA"]) / "Android" / "Sdk")
    adb = sdk / "platform-tools" / "adb.exe"
    def adb_run(*values, input=None, timeout=30):
        try:
            result = subprocess.run([str(adb), *values], input=input, capture_output=True, timeout=timeout)
        except subprocess.TimeoutExpired:
            raise RuntimeError("Owned-app ADB operation timed out; private arguments are not printed.") from None
        if result.returncode:
            raise RuntimeError("Owned-app ADB operation failed; no private output is printed.")
        return result.stdout.decode("utf-8", errors="replace")
    devices = adb_run("devices")
    phones = [row.split()[0] for row in devices.splitlines() if re.fullmatch(r"\S+\s+device", row)
              and not row.startswith("emulator-")]
    if len(phones) != 1:
        parser.error("Exactly one authorized physical phone must be connected.")
    serial = phones[0]
    for apk in (ROOT / "android/app/build/outputs/apk/debug/app-debug.apk",
                ROOT / "android/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk"):
        if "Success" not in adb_run("-s", serial, "install", "-r", str(apk), timeout=60):
            raise RuntimeError("App installation was not approved or did not succeed.")
    private_root = (ROOT / "artifacts" / "pairing").resolve()
    private_root.mkdir(parents=True, exist_ok=True)
    mappings = []
    def device_port(port):
        if args.lan_address:
            return f"https://{bind}:{port}"
        # --no-rebind preserves unrelated existing mappings. Remove only ours.
        remote = f"tcp:{port}"
        adb_run("-s", serial, "reverse", "--no-rebind", remote, remote)
        mappings.append(remote)
        return f"https://127.0.0.1:{port}"
    fixture_path = "no_backup/t008-test.json"
    try:
        with TemporaryDirectory(dir=private_root) as temp:
            directory = Path(temp).resolve()
            if not directory.is_relative_to(private_root):
                raise RuntimeError("Temporary identity directory escaped the owned artifact root.")
            good, wrong = directory / "good", directory / "wrong-host"
            with listener(good, bind) as (port, store), listener(wrong, bind, "10.255.255.254") as (wrong_port, _):
                # Deliberately expired synthetic authority, not a gameplay record.
                past = PairingStore(store.path, clock=lambda: time.time() - 86500)
                expired = past.exchange(past.invite()["invitation"])
                invitation = store.invite()
                fixture = dict(address=device_port(port), fingerprint=fingerprint(good),
                               invitation=invitation["invitation"], expired=expired,
                               wrong_host_address=device_port(wrong_port), wrong_host_fingerprint=fingerprint(wrong))
                adb_run("-s", serial, "shell", "run-as", "org.greedycat.intelligence", "mkdir", "-p", "no_backup")
                adb_run("-s", serial, "shell", "run-as", "org.greedycat.intelligence", "sh", "-c",
                        f"'cat > {fixture_path}'", input=json.dumps(fixture).encode())
                output = adb_run("-s", serial, "shell", "am", "instrument", "-w", "-r", "-e", "class",
                                 "org.greedycat.intelligence.connectivity." + ("SyncIntegrationTest" if args.sync else "PairingIntegrationTest"),
                                 "org.greedycat.intelligence.test/androidx.test.runner.AndroidJUnitRunner", timeout=90)
                # Only redacted JUnit status is emitted. Raw output stays ignored locally.
                label = "lan" if args.lan_address else "usb"
                (private_root / f"{'t009' if args.sync else 't008'}-phone-{label}.txt").write_text(output, encoding="utf-8")
                if re.search(r"INSTRUMENTATION_STATUS_CODE: -[34]", output) or not re.search(r"^OK \(1 test\)\s*$", output, re.MULTILINE):
                    raise RuntimeError("Cross-device pairing test did not report PASS; inspect ignored local JUnit log.")
                if store.list_sessions():
                    raise RuntimeError("Test credential remained active after self-revocation.")
                if args.sync:
                    from greedycat.sync.ingest import SyncStore
                    with closing(SyncStore(good / "research.sqlite3").connect()) as research:
                        if research.execute("SELECT count(*) FROM sync_events").fetchone()[0] != 2 or research.execute("SELECT value FROM sync_reviews ORDER BY revision").fetchall() != [("CORN",), ("TOMATO",)]:
                            raise RuntimeError("PC did not preserve exactly two synthetic review revisions.")
                    print(f"PASS: physical-phone {label.upper()} authenticated sync, actual PC commit/lost ACK/replay, local reopen, preserved correction and revocation; exactly two synthetic PC rows.")
                else:
                    print(f"PASS: physical-phone {label.upper()} TLS pin/hostname rejection, exchange, encrypted reopen, expired credential, replay and revocation.")
    finally:
        operations = [("shell", "run-as", "org.greedycat.intelligence", "rm", "-f", fixture_path)]
        operations += [("reverse", "--remove", mapping) for mapping in mappings]
        operations += [("shell", "am", "force-stop", "org.greedycat.intelligence"),
                       ("shell", "am", "start", "-n", "org.greedycat.intelligence/.MainActivity")]
        failed = False
        for operation in operations:
            try:
                adb_run("-s", serial, *operation)
            except RuntimeError:
                failed = True
        if failed:
            raise RuntimeError("Owned-app test cleanup was incomplete; inspect connection and owned temporary mappings.")


if __name__ == "__main__":
    main()
