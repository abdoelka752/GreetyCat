import asyncio
import unittest
from unittest.mock import patch

import httpx

from greedycat.api.models import Discovery, ServiceError
from greedycat.devices.adb import DeviceDiscovery
from greedycat.main import create_app


class ApiTests(unittest.IsolatedAsyncioTestCase):
    async def request(self, path, *, app=None, client_host="127.0.0.1", headers=None, method="GET"):
        transport = httpx.ASGITransport(app=app or create_app(), client=(client_host, 12345), raise_app_exceptions=False)
        async with httpx.AsyncClient(transport=transport, base_url="http://127.0.0.1:8765") as client:
            return await client.request(method, path, headers=headers)

    async def test_health_is_versioned_and_does_not_touch_adb(self):
        with patch.object(DeviceDiscovery, "discover", side_effect=AssertionError("Health must not call ADB")):
            response = await self.request("/api/v1/health")
        self.assertEqual(200, response.status_code)
        self.assertEqual("OK", response.json()["status"])
        self.assertEqual("v1", response.json()["api_version"])
        self.assertEqual("1.0", response.json()["gameplay_contract_version"])
        self.assertEqual("NOT_IMPLEMENTED", response.json()["pairing"])
        self.assertEqual("no-store", response.headers["cache-control"])

    async def test_device_response_uses_real_discovery_boundary_and_redacts_serials(self):
        data = b"List of devices attached\nsynthetic-only device model:Fake\n"
        with patch("greedycat.devices.adb.resolve_adb", return_value="adb"), \
                patch("greedycat.devices.adb.run_adb", return_value=data) as runner:
            response = await self.request("/api/v1/devices")
        self.assertEqual(200, response.status_code)
        result = Discovery.model_validate(response.json())
        self.assertEqual("AVAILABLE", result.devices[0].state)
        self.assertNotIn("synthetic-only", response.text)
        self.assertNotIn("Fake", response.text)
        runner.assert_called_once_with("adb", 5.0)

    async def test_diagnostic_failures_and_empty_results_have_distinct_status(self):
        for status in ("ADB_MISSING", "ADB_ERROR", "TIMEOUT", "OUTPUT_LIMIT", "INVALID_OUTPUT", "BUSY", "OK"):
            with self.subTest(status=status), patch.object(DeviceDiscovery, "discover", return_value=Discovery(
                status=status, devices=[], observed_at="2026-01-01T00:00:00.000Z", duration_ms=0,
            )):
                response = await self.request("/api/v1/devices")
                self.assertEqual(200, response.status_code)
                self.assertEqual(status, response.json()["status"])

    async def test_health_remains_responsive_while_discovery_runs(self):
        import threading

        entered, release = threading.Event(), threading.Event()

        def slow_discovery():
            entered.set()
            if not release.wait(3):
                raise AssertionError("Test did not release discovery")
            return Discovery(status="OK", observed_at="2026-01-01T00:00:00.000Z", duration_ms=1)

        app = create_app()
        with patch.object(DeviceDiscovery, "discover", side_effect=slow_discovery):
            task = asyncio.create_task(self.request("/api/v1/devices", app=app))
            try:
                self.assertTrue(await asyncio.to_thread(entered.wait, 2))
                health = await asyncio.wait_for(self.request("/api/v1/health", app=app), timeout=1)
                self.assertEqual(200, health.status_code)
            finally:
                release.set()
                await task

    async def test_remote_peers_and_forged_forwarded_headers_are_rejected(self):
        response = await self.request("/api/v1/devices", client_host="192.0.2.10",
                                      headers={"X-Forwarded-For": "127.0.0.1"})
        self.assertEqual(403, response.status_code)
        self.assertEqual("LOCAL_ONLY", response.json()["code"])

    async def test_loopback_diagnostics_do_not_trust_forwarded_headers(self):
        response = await self.request("/api/v1/health", headers={"X-Forwarded-For": "192.0.2.10"})
        self.assertEqual(200, response.status_code)

    async def test_untrusted_hosts_and_browser_origins_are_blocked_before_discovery(self):
        for headers, code in (({"Host": "attacker.invalid"}, "HOST_DENIED"),
                              ({"Host": "localhost.attacker.invalid"}, "HOST_DENIED"),
                              ({"Host": "user@localhost"}, "HOST_DENIED"),
                              ({"Host": "localhost:bad"}, "HOST_DENIED"),
                              ({"Origin": "https://attacker.invalid"}, "ORIGIN_DENIED"),
                              ({"Origin": "null"}, "ORIGIN_DENIED"),
                              ({"Sec-Fetch-Site": "cross-site"}, "ORIGIN_DENIED")):
            with self.subTest(headers=headers), patch.object(DeviceDiscovery, "discover") as probe:
                response = await self.request("/api/v1/devices", headers=headers)
                self.assertEqual(403, response.status_code)
                self.assertEqual(code, response.json()["code"])
                ServiceError.model_validate(response.json())
                probe.assert_not_called()

    async def test_matching_origin_is_allowed_and_cors_is_not_enabled(self):
        response = await self.request("/api/v1/health", headers={"Origin": "http://127.0.0.1:8765"})
        self.assertEqual(200, response.status_code)
        self.assertNotIn("access-control-allow-origin", response.headers)

    async def test_missing_routes_methods_and_docs_are_honest(self):
        for path, method, status, code in (("/api/v1/rounds", "GET", 404, "NOT_FOUND"),
                                           ("/api/v1/devices", "POST", 405, "METHOD_NOT_ALLOWED"),
                                           ("/docs", "GET", 404, "NOT_FOUND")):
            with self.subTest(path=path, method=method):
                response = await self.request(path, method=method)
                self.assertEqual(status, response.status_code)
                self.assertEqual(code, response.json()["code"])

    async def test_openapi_documents_typed_diagnostics_only(self):
        response = await self.request("/api/v1/openapi.json")
        self.assertEqual(200, response.status_code)
        paths = response.json()["paths"]
        self.assertEqual({"/api/v1/health", "/api/v1/devices"}, set(paths))
        self.assertIn("Discovery", response.json()["components"]["schemas"])
        self.assertIn("ServiceError", response.json()["components"]["schemas"])
        self.assertEqual("#/components/schemas/ServiceError",
                         paths["/api/v1/devices"]["get"]["responses"]["403"]["content"]["application/json"]["schema"]["$ref"])

    async def test_unexpected_failure_returns_sanitized_error(self):
        with patch.object(DeviceDiscovery, "discover", side_effect=RuntimeError("private-device-path")):
            response = await self.request("/api/v1/devices")
        self.assertEqual(500, response.status_code)
        self.assertEqual("INTERNAL_ERROR", response.json()["code"])
        self.assertNotIn("private-device-path", response.text)


class LauncherTests(unittest.TestCase):
    def test_launcher_enforces_loopback_single_worker_and_no_proxy_trust(self):
        from greedycat.server import main
        with patch("sys.argv", ["greedycat.server", "--port", "8766"]), patch("uvicorn.run") as run:
            main()
        kwargs = run.call_args.kwargs
        self.assertEqual("127.0.0.1", kwargs["host"])
        self.assertEqual(1, kwargs["workers"])
        self.assertFalse(kwargs["proxy_headers"])
        self.assertFalse(kwargs["access_log"])

    def test_launcher_rejects_host_override_and_invalid_port(self):
        from greedycat.server import main
        for args in (["--host", "0.0.0.0"], ["--port", "0"], ["--port", "65536"]):
            with self.subTest(args=args), patch("sys.argv", ["greedycat.server"] + args), \
                    patch("sys.stderr"), self.assertRaises(SystemExit) as caught:
                main()
            self.assertEqual(2, caught.exception.code)


if __name__ == "__main__":
    unittest.main()
