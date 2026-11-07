from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

import httpx

from greedycat.devices.adb import DeviceDiscovery
from greedycat.main import create_app


class DashboardServingTests(unittest.IsolatedAsyncioTestCase):
    async def asyncSetUp(self):
        self.directory = tempfile.TemporaryDirectory()
        self.root = Path(self.directory.name)
        self.dist = self.root / "dist"
        (self.dist / "assets").mkdir(parents=True)
        (self.dist / "index.html").write_text('<html><h1>Synthetic dashboard fixture</h1></html>', encoding="utf-8")
        (self.dist / "assets" / "app.js").write_text('console.log("synthetic fixture")', encoding="utf-8")
        (self.root / "private.txt").write_text("PRIVATE-TEST-FIXTURE", encoding="utf-8")
        self.app = create_app(frontend_dir=self.dist)
        self.client = httpx.AsyncClient(transport=httpx.ASGITransport(app=self.app, client=("127.0.0.1", 1)),
                                        base_url="http://127.0.0.1:8765")

    async def asyncTearDown(self):
        await self.client.aclose()
        self.directory.cleanup()

    async def test_static_dashboard_and_assets_share_api_origin_and_policy(self):
        with patch.object(DeviceDiscovery, "discover", side_effect=AssertionError("Static files cannot probe devices")):
            response = await self.client.get("/")
            self.assertEqual(200, response.status_code)
            self.assertIn("Synthetic dashboard fixture", response.text)
            self.assertEqual("no-store", response.headers["cache-control"])
            self.assertIn("connect-src 'self'", response.headers["content-security-policy"])
            self.assertNotIn("unsafe-inline", response.headers["content-security-policy"])
            self.assertEqual(200, (await self.client.get("/assets/app.js")).status_code)

    async def test_api_is_not_shadowed_and_unknown_routes_stay_404(self):
        self.assertEqual("OK", (await self.client.get("/api/v1/health")).json()["status"])
        self.assertEqual(404, (await self.client.get("/api/v1/rounds")).status_code)
        self.assertEqual(404, (await self.client.get("/not-a-dashboard-route")).status_code)

    async def test_static_content_is_guarded_and_cannot_escape_dist(self):
        self.assertEqual(403, (await self.client.get("/", headers={"Host": "untrusted.invalid"})).status_code)
        self.assertEqual(403, (await self.client.get("/", headers={"Origin": "https://untrusted.invalid"})).status_code)
        for path in ("/../private.txt", "/assets/..%2F..%2Fprivate.txt", "/%2e%2e/private.txt"):
            with self.subTest(path=path):
                response = await self.client.get(path)
                self.assertEqual(404, response.status_code)
                self.assertNotIn("PRIVATE-TEST-FIXTURE", response.text)

    async def test_unbuilt_frontend_does_not_disable_health(self):
        app = create_app(frontend_dir=self.root / "not-built")
        async with httpx.AsyncClient(transport=httpx.ASGITransport(app=app, client=("127.0.0.1", 1)),
                                    base_url="http://127.0.0.1:8765") as client:
            self.assertEqual(200, (await client.get("/api/v1/health")).status_code)
            self.assertEqual(404, (await client.get("/")).status_code)
