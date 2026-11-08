# File-level implementation plan

Paths below are planned except T002/T003/T004/T005, which are implemented as recorded in their rows. Consult current official documentation for platform/API behavior and pin compatible versions in the task that introduces dependencies. Prefer one Android app module initially.

| Task | Planned files | Primary responsibility |
|---|---|---|
| T002 (implemented) | `contracts/v1/` (common, event, round, outcome, prediction, errors schemas), `contracts/fixtures/`, `contracts/README.md`, `backend/greedycat/contracts.py`, `backend/requirements-contracts.txt`, `backend/tests/test_contracts.py` | Shared structural/semantic validation, replay/time guards, synthetic conformance fixtures and offline CLI |
| T003 (implemented) | `backend/requirements.txt`, `backend/greedycat/main.py`, `server.py`, `api/health.py`, `api/models.py`, `devices/adb.py`, `backend/tests/test_health.py`, `test_devices.py`, `backend/README.md`, `tools/start-server.ps1`, `tools/smoke_server.py` | Loopback-only health, typed diagnostics, bounded read-only discovery and actual HTTP/ADB verification; source-checkout launch rather than packaging |
| T004 (implemented) | `web/package.json`, `package-lock.json`, `src/App.tsx`, `src/api/client.ts`, `src/useDiagnostics.ts`, `src/components/Icon.tsx`, CSS, unit/browser tests, `web/README.md`, `backend/greedycat/main.py`, `backend/tests/test_dashboard.py`, `tools/start-dev.ps1`, `tools/smoke_server.py` | Two functional diagnostic views, same-origin serving, explicit states, selected-device details, opt-in polling, actual browser/Windows verification; views stay in one bounded App component initially |
| T005 | `android/settings.gradle.kts`, `build.gradle.kts`, `gradle/libs.versions.toml`, Gradle wrapper, `app/build.gradle.kts`, `app/src/main/AndroidManifest.xml` | Reproducible pinned Android build and manifest |
| T005 | `android/app/src/main/java/org/greedycat/intelligence/MainActivity.kt`, `ui/Navigation.kt`, `ui/Theme.kt`, `data/ResearchDatabase.kt`, `data/Entities.kt`, `data/ResearchRepository.kt` | Navigation, injection boundary, Room schema and transactions |
| T006 | Android package files `ingestion/ReferenceImporter.kt`, `feature/review/ReviewScreen.kt`, `feature/history/HistoryScreen.kt`, `domain/RoundValidator.kt` | Import, hashes, manual verified annotation, provenance and history |
| T007 | Android `statistics/DirichletEstimator.kt`, `statistics/Uncertainty.kt`, `domain/PredictionLocker.kt`, `feature/live/LiveScreen.kt` | Standalone estimates and immutable, pre-result predictions |
| T008 | Backend `security/pairing.py`, `security/sessions.py`, `api/pairing.py`; Android `connectivity/PairingClient.kt` | Pair/revoke, secure token storage, encrypted transport and certificate trust |
| T009 | Android `data/Outbox.kt`, `connectivity/SyncWorker.kt`; backend `storage/schema.sql`, `storage/migrations/`, `sync/ingest.py`, `sync/conflicts.py`, `api/events.py` | Durable retry-safe delivery, transactions, corrections and audit |
| T010 | Backend `statistics/baseline.py`, `api/rounds.py`, `api/predictions.py`; web `pages/Live.tsx`, `pages/History.tsx`; Android `connectivity/ResultGate.kt`; `tests/integration/` | Roundtrip integration, independent PC estimate and late/wrong-round result handling |
| T011 | Android `vision/Detector.kt`, `vision/Calibration.kt`, `gamestate/RoundStateMachine.kt`; backend `vision/reprocess.py`; `tests/fixtures/manifest.json` | Confidence/evidence contracts, anchors, temporal validation and review |
| T012 | Android `capture/CaptureService.kt`, `capture/ProjectionController.kt`, `capture/FrameQueue.kt`, `feature/observer/ObserverScreen.kt` | Consented capture lifecycle, bounded frames, visible stop and diagnostics |
| T013 | Android `ingestion/RoundCollector.kt`; backend `api/live.py`; web `api/live.ts` | Confirmed automatic collections and live event state |
| T014 | Backend `evaluation/scoring.py`, `walk_forward.py`, `calibration.py`, `promotion.py`; web `pages/Laboratory.tsx` | Chronological metrics, negative controls and promotion evidence |
| T015 | Backend `research/jobs.py`, `hypotheses.py`, `drift.py`, `datasets.py` | Cancellable reproducible studies and multiple-testing correction |
| T016 | Backend `models/registry.py`, `artifacts.py`; Android `learning/ModelInstaller.kt`; web `pages/Models.tsx` | Dataset lineage, checksums, compatibility and rollback |
| T017 | Backend `decision/payouts.py`, `risk.py`, `simulation/scenarios.py`; Android `decision/DecisionEngine.kt`; web `pages/Simulator.tsx` | Verified matrix, exact outcomes, synthetic provenance and abstention |
| T018 | Backend `resources/monitor.py`, `scheduler.py`, `budgets.py`; web `pages/Resources.tsx` | Measured resource budgets, bounded workers and latency-aware scheduling |
| T019 | Backend `data/export.py`, `backup.py`, `retention.py`; Android `data/ExportManager.kt`, `feature/settings/SettingsScreen.kt` | Local data ownership, bounded retention, migrations and recovery |
| T020 | `tools/setup.ps1`, `tools/start-dev.ps1`, `docs/DEVELOPMENT.md`, `docs/DEVICE_SETUP.md`; all shipped UI routes | Documented Windows workflow and functional final screens |
| T021 | `tests/e2e/`, `docs/reports/DEVICE_VALIDATION.md`, `docs/reports/PERFORMANCE.md`, `docs/reports/MODEL_EVALUATION.md` | Actual device/network/load/latency evidence and known limits |

T005 settled application ID/package `org.greedycat.intelligence`. Its two file rows are implemented, with `ResearchApplication.kt`, `domain/ResearchModels.kt`, `domain/DemoSample.kt`, `data/ResearchDao.kt`, `ui/ResearchViewModel.kt`, generated Room schemas, JVM/instrumentation tests, Android README and isolated emulator helpers. Paths for future Android features remain proposed. Paths sharing a cell are relative to the last explicit component base unless stated otherwise. Every code task includes meaningful unit/integration tests alongside its files.

## Prioritized risks

1. **Critical: absent game evidence.** Do not create result templates or authoritative payout matrices from prose. Implement manual review and abstention first; capture rules/full rounds when authorized.
2. **Critical: temporal leakage and mutable predictions.** Establish immutable contracts and dataset cutoff tests before any performance reporting.
3. **High: synchronization conflicts and identity resets.** Keep source context and revisions; reject conflicting reuse of event IDs, never silently replace corrections.
4. **High: capture consent and lifecycle.** Test foreground service, revocation and interruption against current Android documentation and actual device APIs.
5. **High: local transport security.** Loopback first; pairing, revocation and encrypted trust precede LAN exposure.
6. **High: machine memory limits.** A single worker and bounded queues precede optional acceleration; record limits and actual measurements.
7. **Medium: platform compatibility.** Python 3.13 and installed SDKs do not guarantee dependency compatibility. Resolve/pin/test before adopting versions.

## First hybrid slice acceptance (T010)

Import or manually annotate explicit test evidence on Android, validate/store it in Room, disconnect/reconnect and synchronize idempotently, calculate a versioned PC probability estimate, display the actual round in web history, and return a compatible non-stale estimate to Android. Demonstrate that already locked predictions remain unchanged and unconfirmed results never become training outcomes. This proves integration, not automated recognition or predictive skill.
