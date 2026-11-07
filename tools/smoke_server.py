"""Start/stop the actual loopback server and probe HTTP + installed ADB safely."""

import argparse
import json
from pathlib import Path
import socket
import subprocess
import sys
import tempfile
import time

import httpx

ROOT = Path(__file__).resolve().parents[1]


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--require-discovery", action="store_true",
                        help="Fail unless installed ADB returns a valid device list (possibly empty).")
    args = parser.parse_args()
    with socket.socket() as reservation:
        reservation.bind(("127.0.0.1", 0))
        port = reservation.getsockname()[1]
    with tempfile.TemporaryFile() as output:
        flags = subprocess.CREATE_NO_WINDOW if sys.platform == "win32" else 0
        process = subprocess.Popen([sys.executable, "-m", "greedycat.server", "--port", str(port)],
                                   cwd=ROOT / "backend", stdin=subprocess.DEVNULL,
                                   stdout=output, stderr=output, creationflags=flags)
        try:
            with httpx.Client(base_url=f"http://127.0.0.1:{port}", trust_env=False, timeout=12) as client:
                deadline = time.monotonic() + 10
                while True:
                    if process.poll() is not None:
                        raise RuntimeError("Local server exited before becoming ready")
                    try:
                        health = client.get("/api/v1/health")
                        if health.status_code == 200:
                            break
                    except httpx.TransportError:
                        pass
                    if time.monotonic() >= deadline:
                        raise RuntimeError("Local server readiness timed out")
                    time.sleep(0.1)
                health.raise_for_status()
                assert health.json()["status"] == "OK"
                response = client.get("/api/v1/devices")
                response.raise_for_status()
                devices = response.json()
                assert devices["schema_version"] == "1.0"
                assert "no-store" in response.headers["cache-control"]
                assert client.get("/api/v1/health", headers={"Host": "untrusted.invalid"}).status_code == 403
                assert client.get("/api/v1/devices", headers={"Origin": "https://untrusted.invalid"}).status_code == 403
                assert client.get("/api/v1/rounds").status_code == 404
                assert client.get("/api/v1/openapi.json").status_code == 200
                if args.require_discovery and devices["status"] != "OK":
                    raise RuntimeError(f"Installed ADB discovery returned {devices['status']}")
                print(json.dumps({"result": "PASS", "transport": "actual loopback HTTP",
                                  "health": health.json()["status"], "discovery": devices["status"],
                                  "device_count": len(devices["devices"]),
                                  "device_states": [item["state"] for item in devices["devices"]],
                                  "discovery_duration_ms": devices["duration_ms"],
                                  "access_guards": "PASS", "unimplemented_route": "404"}))
            return 0
        except (httpx.HTTPError, RuntimeError, AssertionError, KeyError):
            # Never dump raw server logs or device-response bodies.
            print("FAIL: local server smoke check failed; inspect local diagnostics without publishing device identifiers.")
            return 1
        finally:
            if process.poll() is None:
                process.terminate()
                try:
                    process.wait(timeout=5)
                except subprocess.TimeoutExpired:
                    process.kill()
                    process.wait(timeout=5)


if __name__ == "__main__":
    raise SystemExit(main())
