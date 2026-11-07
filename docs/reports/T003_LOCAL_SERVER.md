# T003 — local service and read-only device discovery

Date: 2026-11-07. Scope: a working loopback health/device server, not Android screen observation or game analysis.

## Delivered behavior

FastAPI implements `GET /api/v1/health`, `GET /api/v1/devices` and typed diagnostic OpenAPI at `/api/v1/openapi.json`. Health reports API/gameplay contract versions and pairing NOT_IMPLEMENTED without invoking ADB. Unimplemented gameplay routes return 404. Service errors use a typed diagnostic envelope with the same version/code/path/message/retryable structure as T002 errors but separate operational enums; persisted gameplay contracts are unchanged.

Discovery executes only `adb -H 127.0.0.1 -P 5037 devices -l`, with no shell, redirected stdin, remote-server environment overrides removed and no visible console. It supports missing executable, execution failure, timeout, malformed/oversized output, no devices, unauthorized/offline/other transport states and concurrent BUSY. Default timeout is 5 seconds; retained output is capped at 64 KiB, and temporary-file size is polled every 20 ms (transient disk writes can overshoot before termination). Up to 64 devices are parsed. One subprocess runs at a time; synchronous ADB work runs off the API event loop.

Raw serials/model/product labels are not returned. Session-keyed HMAC pseudonyms are stable during one server process and change on restart. AVAILABLE indicates ADB transport availability only; screen availability, game visibility, capture and analyzer status remain UNKNOWN. Listing may start the local ADB daemon. This task never runs commands on a phone or stops/resets the shared daemon.

The supported launcher binds 127.0.0.1, uses one worker and disables access logs and proxy-header trust. API guards reject remote peers, nonlocal Host headers, mismatched browser Origins and cross-site fetches. CORS is not enabled. Other local processes can access diagnostics; authenticated LAN pairing and encryption are still T008.

## Verification evidence

Environment: Windows / CPython 3.13.4. Installed direct service/test versions: FastAPI 0.162.1, Uvicorn 0.54.0, HTTPX 0.28.1. Full resolved dependency pins are in `backend/requirements.txt` plus `requirements-contracts.txt`. Plain FastAPI/Uvicorn installations avoid optional cloud CLI, reload and acceleration stacks.

| Check | Command | Result |
|---|---|---|
| Full suite (from backend/) | `..\.venv\Scripts\python.exe -m unittest discover -s tests -v` | PASS: 52 tests; 25 existing contract, 14 discovery, 13 API/launcher |
| Actual HTTP + installed ADB (root) | `.\.venv\Scripts\python.exe tools/smoke_server.py --require-discovery` | PASS: health OK, discovery OK, one AVAILABLE transport, access guards PASS, absent gameplay route 404 |
| Dependencies | `.\.venv\Scripts\python.exe -m pip check` | PASS: no broken requirements |
| Documents/provenance | `python tools/verify_project_docs.py` | PASS |
| PowerShell launcher | PowerShell parser `ParseFile` on tools/start-server.ps1 | PASS: syntax only; smoke invokes underlying Python launcher |

The final recorded smoke query took approximately 114.9 ms for ADB discovery on this host. An earlier query took approximately 132.2 ms. These are individual diagnostic-call samples, not end-to-end observation latency or a performance benchmark. No device identifiers or raw ADB output are included in this report. The smoke server was terminated/reaped after each run; the shared ADB daemon was left alone.

The suite tests actual synthetic child-process timeout/output-limit/nonzero-exit behavior and cleanup, not just mocks of subprocess results. API tests use HTTPX ASGITransport with explicit loopback peers. Tests check missing/unauthorized/offline/unknown states, output parsing, privacy, concurrent BUSY behavior, responsiveness during blocked discovery, sanitized errors, fixed launcher configuration, disallowed host/origin/peer requests, OpenAPI response types and absent routes. No test issues phone commands.

## Limits and decisions

No screenshot/capture, foreground app visibility, physical-device app launch, pairing, round persistence/synchronization, model computation, web dashboard or predictive performance was tested or implemented. A real AVAILABLE ADB transport does not establish those capabilities. No game data was collected.

This task adds pinned source-checkout dependencies and startup commands, rather than a pyproject/wheel installer. The existing gameplay validator resolves repository-level schemas, so packaging both schemas and code remains a separate concern. Diagnostic response contracts are typed Pydantic/OpenAPI models; no changes to the T002 v1.0 gameplay schema semantics were required.

The PowerShell wrapper passed parser validation. The actual-socket smoke exercised the Python module that wrapper invokes; interactive Ctrl+C through the wrapper was not separately tested. Local-only middleware does not substitute for later device authentication or encrypted LAN transport. T004's frontend must integrate without bypassing origin/Host checks.

Official sources consulted on 2026-11-07: [FastAPI work dispatch](https://fastapi.tiangolo.com/async/), [FastAPI testing](https://fastapi.tiangolo.com/tutorial/testing/), [Uvicorn listener/proxy settings](https://uvicorn.dev/settings/), [Android ADB](https://developer.android.com/tools/adb), and maintainer metadata for [FastAPI](https://pypi.org/project/fastapi/), [Uvicorn](https://pypi.org/project/uvicorn/) and [HTTPX](https://pypi.org/project/httpx/). Tests use ASGITransport, avoiding Starlette's deprecated HTTPX TestClient adapter.

Next task: T004, functional React dashboard and Windows server/dashboard startup. Final staged-diff checks and commit/push are the delivery checkpoint; the delivery message records the verified GitHub hash.
