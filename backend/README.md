# Local diagnostic server (T003)

The diagnostic server implements health and read-only ADB transport discovery. It does not capture screens, inspect applications, navigate a phone, synchronize rounds or calculate predictions. T008 adds a **separate HTTPS pairing service**, loopback by default, with Android operator-confirmed certificate trust, encrypted credential storage and explicit revocation. See [PC pairing foundation](../docs/reports/T008A_PC_PAIRING.md) and [Android pairing, opt-in private-IP operation and current evidence](../docs/reports/T008B_ANDROID_PAIRING.md). The diagnostic health field `pairing: NOT_IMPLEMENTED` still describes this HTTP service, which deliberately cannot exchange credentials.

T004 also serves the compiled React dashboard from `web/dist/` at `/`. Use the [dashboard startup instructions](../web/README.md) for the full UI. API-only operation remains supported when the dashboard is not built.

## Windows setup and run

From the repository root, with Python 3.13 available:

```powershell
python -m venv .venv
.\.venv\Scripts\python.exe -m pip install -r backend/requirements.txt
.\tools\start-server.ps1
```

The server runs in the foreground at `http://127.0.0.1:8765`. Stop it with Ctrl+C. An alternate local port can be selected with `-Port 8766`. No host override is supported by the launcher. It uses one worker, disables proxy-header trust and access logs, and does not enable reload/background workers. It requires no cloud account or subscription.

If PowerShell execution policy prevents the script from running, use the equivalent Python command from repository root:

```powershell
Push-Location backend
..\.venv\Scripts\python.exe -m greedycat.server --port 8765
Pop-Location
```

In another terminal:

```powershell
Invoke-RestMethod http://127.0.0.1:8765/api/v1/health
Invoke-RestMethod http://127.0.0.1:8765/api/v1/devices
```

There is no packaged installer yet; run from this source checkout so repository-level gameplay schemas remain available. Runtime and test dependencies are pinned together in `requirements.txt`, which includes the earlier contract dependency set. No editable/wheel packaging step is required.

## API behavior

| Route | Behavior |
|---|---|
| `GET /api/v1/health` | Liveness, API/gameplay contract versions, local-only mode and pairing NOT_IMPLEMENTED; independent of ADB |
| `GET /api/v1/devices` | One bounded `adb devices -l` query; typed status and device list, observed timestamp and measured query duration |
| `GET /api/v1/openapi.json` | Typed diagnostic response and service error contracts |

The API uses schema version `1.0`. Diagnostic models and service errors are described in OpenAPI; they are distinct from persisted gameplay schemas under `contracts/v1/`. This task does not widen or reinterpret the T002 gameplay error enums. Device failures use an HTTP 200 diagnostic response with an explicit non-OK discovery status, so clients can distinguish a running service from unavailable diagnostics. Access failures use HTTP 403; absent routes return 404; unsupported methods return 405. No placeholder gameplay endpoints exist.

Discovery statuses: OK (possibly an empty device list), ADB_MISSING, ADB_ERROR, TIMEOUT, OUTPUT_LIMIT, INVALID_OUTPUT, or BUSY. One discovery subprocess can run at a time; another caller receives BUSY immediately. The default process timeout is 5 seconds. Retained output is limited to 64 KiB; output-file size is polled every 20 ms, so temporary-file writes can briefly overshoot that threshold before termination. At most 64 devices are accepted. Raw output is never returned or logged.

Device states: AVAILABLE means the ADB transport reports `device`; UNAUTHORIZED means ADB reports `unauthorized`; OFFLINE means `offline`; other transport states remain UNKNOWN. Authorization is true, false, or unknown accordingly. AVAILABLE does not prove Android has finished booting. Screen availability, game visibility, capture and analyzer state are always UNKNOWN in T003.

Response device IDs are HMAC-derived pseudonymous UUIDs, stable only for the current server session. The per-session key is not persisted. Raw serials, network device addresses, model names and product names are excluded from responses and reports. These diagnostic IDs are not the durable identity used by future authenticated pairing.

## ADB setup and scope

Install Android SDK Platform Tools and place `adb` on PATH, or set `ANDROID_HOME` / `ANDROID_SDK_ROOT` to an SDK containing `platform-tools`. Resolution checks PATH first and then those SDK variables. Only use a trusted SDK/executable. The Python discovery class also supports an explicit trusted executable path for local integration/testing; no HTTP parameter selects commands or executable paths.

For a phone you own and wish to connect, enable Developer options and USB debugging, connect a data-capable USB cable, and accept the debugging authorization prompt only if you intend to trust this computer. A listed unauthorized/offline device requires user attention; this server never approves debugging, reconnects Wi-Fi devices, installs apps or changes device settings. Existing wireless ADB transports may be listed, but the service does not initiate pairing/connections.

The exact command is `adb -H 127.0.0.1 -P 5037 devices -l`, with redirected stdin and no shell. Remote ADB server environment overrides are removed from the child environment. Listing devices may start the local ADB daemon; the server does not stop or reset that shared daemon. It never runs `shell`, `input`, `screencap`, `screenrecord`, `connect`, `pair`, `install` or game actions. ADB authorization alone is not permission for later screen capture/navigation.

## Local access boundary

The diagnostic launcher binds IPv4 loopback only. Middleware rejects non-loopback peers and non-local Host headers, mismatched/multiple browser Origins, and cross-site fetches. Proxy headers cannot assert local identity. CORS is disabled and responses use no-store. No access log contains URLs or identifiers. This is a local diagnostics boundary, not device authentication; other local processes can call it. Never expose this diagnostic HTTP service through a LAN/public reverse proxy. T008 uses a separate native HTTPS listener with explicit authentication; it does not expose the dashboard or ADB diagnostics.

T004 uses same-origin static serving from this FastAPI process. No Vite HTTP proxy or CORS exception is required. Dashboard assets receive a same-origin Content Security Policy. Unknown API and static paths still return 404; there is no catch-all fake-success page. Static serving stays within the build directory.

## Verification

```powershell
Push-Location backend
..\.venv\Scripts\python.exe -m unittest discover -s tests -v
Pop-Location
.\.venv\Scripts\python.exe tools/smoke_server.py --require-discovery
.\.venv\Scripts\python.exe -m pip check
python tools/verify_project_docs.py
```

The smoke tool starts a real loopback HTTP server, checks health and installed ADB discovery, verifies access rejections, prints only redacted aggregate results, then stops its server process. `--require-discovery` requires a valid ADB list, even if empty. Omitting the flag also accepts a correctly reported ADB diagnostic failure. This does not prove screen capture, app visibility, Android integration or model performance.

Current official references: [FastAPI synchronous work/thread dispatch](https://fastapi.tiangolo.com/async/), [Uvicorn host and proxy settings](https://uvicorn.dev/settings/), [Android ADB device states](https://developer.android.com/tools/adb), and [FastAPI API testing](https://fastapi.tiangolo.com/tutorial/testing/). Consulted 2026-11-07. Tests use HTTPX ASGITransport plus an actual-socket smoke check, without Starlette's deprecated HTTPX TestClient adapter.
