# Local research dashboard (T004)

React/TypeScript dashboard connected to the actual local health and ADB diagnostic APIs. It provides overview/device views, device selection, manual refresh, explicit loading/failure/empty states and optional 15-second polling while visible. It does not display synthetic game outcomes, probability estimates or resource telemetry.

## Windows setup

Requires Python 3.13 and Node 22.12+ (tested with Node 22.16.0 and npm 10.9.2). From repository root:

```powershell
python -m venv .venv
.\.venv\Scripts\python.exe -m pip install -r backend/requirements.txt
Push-Location web
npm.cmd ci
Pop-Location
.\tools\start-dev.ps1
```

Open `http://127.0.0.1:8765`. The launcher typechecks/builds the dashboard, then runs the local FastAPI server in the foreground. Stop with Ctrl+C. Use `-Port 8766` to change the local port. It does not open a browser/sign-in dialog or contact a hosted service. Installation needs package access once; running the installed build requires no cloud service.

If PowerShell script policy blocks startup, run the equivalent commands directly:

```powershell
Push-Location web
npm.cmd run build
Pop-Location
Push-Location backend
..\.venv\Scripts\python.exe -m greedycat.server --port 8765
Pop-Location
```

The UI bundle is served by FastAPI from `web/dist/`, under the same peer/Host/Origin guards as the API. No Vite HTTP proxy or wildcard CORS is needed. Build output is ignored by Git; build it after cloning before starting the dashboard. If no build exists, API health/discovery still work and `/` returns 404.

For editing, run `npm.cmd run watch` from `web/` in a second terminal; it rebuilds the bundle when sources change. Refresh the browser after the build finishes. There is no separate Vite dev HTTP server or HMR socket. This keeps UI/API on one guarded loopback origin. The two view buttons are in-app navigation; there are no deep-link routes yet.

## Data and interaction semantics

- All production data comes from `/api/v1/health` and `/api/v1/devices`. The client validates version, shape, enum values, authorization consistency, timestamps and finite durations before rendering them.
- Diagnostics are snapshots, not proof of a continuing live connection. Last-check time and refresh mode remain visible. A new check clears previous success; failures cannot leave stale green device/server results on screen.
- Health and discovery settle independently. A slow/failed ADB query does not hide successful server health.
- Manual refresh is the default. Optional auto refresh starts one check every 15 seconds only while the document is visible. Checks never overlap; requests time out after 8 seconds. Unmount cancels outstanding requests. Hidden pages do not start new checks; an existing check may finish.
- A successful empty ADB list says No devices found. Missing ADB, execution failure, timeout, invalid/oversized output and BUSY all have distinct actionable messages. Unauthorized, offline and unknown transports remain distinct from available transports.
- Device details show only a prefix of the backend's ephemeral session pseudonym. Hardware serial/model/product data is never requested. Capture, game visibility and analyzer capabilities stay Unknown. Pairing, round history, models and prediction generation are not available in this slice.
- No request places wagers, controls a phone, or starts capture. There are no buttons for unimplemented actions. The observation panel describes pending functionality rather than inventing gameplay data.

The compact dark interface uses system fonts, CSS and small local SVGs; it loads no remote fonts/images or analytics. These are companion-workspace visuals, not extracted game artwork. CSP allows same-origin assets/connections without inline scripts. Modern Chromium was tested; other browsers and actual mobile devices have not been verified. The mobile result is a browser viewport check, not an Android app/device test.

## Verification

From `web/`:

```powershell
npm.cmd test
npm.cmd run build
$env:PLAYWRIGHT_SKIP_BROWSER_GC = '1'
npx.cmd playwright install chromium
npm.cmd run test:e2e
```

The isolated headless browser has no access to your signed-in browser profile. Tests use one worker. One browser test uses the real server/ADB response; failure-state tests explicitly intercept responses with synthetic fixtures. No fixture mode or mock server is shipped in the production bundle. Screenshots are saved locally to ignored `artifacts/T004/desktop.png` and `artifacts/T004/mobile.png`.

From repository root, verify the actual Windows build/start process and real HTTP/ADB queries:

```powershell
.\.venv\Scripts\python.exe tools/smoke_server.py --dashboard --require-discovery
```

That smoke check starts its own hidden launcher process tree and stops only that tree afterward. It leaves the shared ADB daemon alone. It requires an available ADB executable and a valid list, which may be empty. The browser test server uses port 18765 and shuts down after tests; normal startup uses 8765.

See the [T004 evidence report](../docs/reports/T004_DASHBOARD.md). Official references consulted: [React effect cleanup](https://react.dev/reference/react/useEffect), [Vite production builds](https://vite.dev/guide/build), [Vitest setup](https://vitest.dev/guide/) and [Playwright local-server testing](https://playwright.dev/docs/test-webserver).
