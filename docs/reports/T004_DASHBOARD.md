# T004 — functional local diagnostic dashboard

Date: 2026-11-07. Scope: React UI on the existing real health/device APIs, error/empty states, Windows startup and browser validation. Android/game observation remains outside this task.

## Working data flow

`Browser -> same-origin FastAPI -> health / bounded ADB discovery -> validated client response -> overview/device views`.

The built Vite bundle is served from `web/dist/` by the existing guarded FastAPI process. No proxy, wildcard CORS, extra network listener, cloud hosting or subscription was added. Root/static requests share the existing local peer/Host/Origin checks and a same-origin Content Security Policy. Unknown API/static routes stay 404. Health APIs remain usable without a web build.

The dashboard implements navigation, device selection/details, manual refresh, and opt-in polling every 15 seconds while visible. Health/device requests settle independently, each times out after 8 seconds, checks cannot overlap, unmount cancels pending requests, and refresh clears old success states. It distinguishes a valid empty list from missing ADB, execution error, timeout, oversized/invalid output and concurrent BUSY. Available/unauthorized/offline/unknown transport states remain distinct. No controls pretend to start capture, pairing, training or wagering.

Client parsing rejects incompatible schemas, invalid dates/nonfinite durations, duplicate IDs, contradictory authorization and invented capture capabilities. Device IDs are ephemeral backend pseudonyms; raw hardware identifiers are not collected. Production renders no fake rounds, probabilities, performance charts or telemetry. Future observation/model functions are explicitly unavailable/unknown.

## Final verification

Environment: Windows 10, Python 3.13.4, Node 22.16.0, npm 10.9.2, isolated headless Chromium 158.0.8127.18. Dependencies are exact-pinned in package.json/package-lock.json; React 19.3.1, Vite 8.3.6, TypeScript 7.0.2, Vitest 5.1.0 and Playwright 1.65.0.

| Check | Command | Result |
|---|---|---|
| Backend including static/origin/path checks (backend/) | `..\.venv\Scripts\python.exe -m unittest discover -s tests -v` | PASS: 56 tests |
| Client compatibility/error/timeout tests (web/) | `npm.cmd test` | PASS: 9 tests |
| Strict TypeScript and production bundle (web/) | `npm.cmd run build` | PASS; approximately 239 kB JS / 74.2 kB gzip and 15.1 kB CSS / 4.0 kB gzip |
| Actual browser behavior (web/) | `npm.cmd run test:e2e` | PASS: 7 tests, one worker, final full run 9.3 seconds |
| Actual Windows build/start + HTTP + installed ADB (root) | `.\.venv\Scripts\python.exe tools/smoke_server.py --dashboard --require-discovery` | PASS: dashboard launcher, health, valid empty ADB list, access guards and 404 behavior |
| Runtime dependency advisory check (web/) | `npm.cmd audit --omit=dev --audit-level=moderate` | PASS: zero reported vulnerabilities at check time |
| Python dependency consistency | `.\.venv\Scripts\python.exe -m pip check` | PASS |

The real browser API test consumed actual local ADB results. Latest actual discovery returned zero devices; it was not replaced with synthetic production data. Fault/selection/polling browser cases use explicit intercepted synthetic responses only in test code. The latest launcher smoke measured approximately 84.8 ms for its individual ADB query; this is not an observation-latency benchmark or a continued device-connection claim.

Browser cases cover actual API navigation/refresh, loading and empty-to-device recovery, all six discovery failure statuses, unauthorized/offline/unknown selection, backend disconnection/incompatible payload/HTTP denial, opt-in polling with hidden-page suppression, and keyboard navigation/phone layout. Desktop 1440x1100 and phone 390x844 screenshots were generated and visually inspected; phone scroll width fit the viewport. No page JavaScript errors occurred in the actual API test. Screenshot artifacts remain local under ignored `artifacts/T004/`.

## Failures found and corrected

- A sandboxed Node invocation failed with `EPERM ... lstat C:\Users\TAWAB`. The permitted execution environment built and tested successfully; this was an environment restriction, not an application test failure.
- The first full browser run passed six checks and timed out on the custom auto-refresh switch: its decorative track intercepted the input click. The track now ignores pointer events. The targeted polling check and the subsequent full seven-test run passed.
- The first actual Windows launcher smoke found that an em dash in a UTF-8-no-BOM PowerShell file broke Windows PowerShell 5.1 parsing. The launcher now uses ASCII text. The actual build/start/HTTP/ADB smoke then passed.

## Limits and decisions

The interactive Browser execution tool was unavailable in this session after discovery; QA used a separate headless Playwright browser, with no signed-in user profile. This is real Chromium/browser validation, not in-app Browser validation. Other browsers, assistive technology, physical-phone web rendering and the Android application are NOT TESTED.

UI views stay in one App component for this bounded two-view slice, with separate API/hook/icon/style modules. Vite is used as a compiler; development watch rebuilds static output followed by manual browser refresh rather than adding a second HTTP/HMR service. Same-origin serving was chosen to preserve the existing security boundary without a new trusted proxy.

No real capture, game visibility, round synchronization, model output, evaluation, pairing or wagering was implemented or claimed. The actual launcher harness stopped only its own process tree; browser tests shut down their server. Shared ADB infrastructure was left alone. Interactive Ctrl+C was not separately exercised. Website source is saved to the user-authorized GitHub repository; no hosted Site was registered or published.

Official references consulted: [React effect lifecycle](https://react.dev/reference/react/useEffect), [Vite production builds](https://vite.dev/guide/build), [Vitest setup](https://vitest.dev/guide/), and [Playwright local-server testing](https://playwright.dev/docs/test-webserver). Next task: T005 Android foundation. Final documentation/whitespace review and Git commit/push form the delivery checkpoint.
