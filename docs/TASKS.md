# Task board

Each row is a reviewable task with a commit and push after passing its acceptance checks. TODO is not implementation. T002 is the current checkpoint; publication evidence is recorded in the delivery message and subsequent status checkpoint.

| Task | Status | Dependencies | Deliverable and completion evidence |
|---|---|---|---|
| T001 | DONE — pushed f570faa | none | Audit, directives, architecture, task board and validation plan; checks PASS; remote main matched f570faa656ac851a7811211ec8ed5bbaa3c34a74 |
| T002 | DONE — local checks PASS | T001 | Six v1 schemas, five synthetic valid records, 29 invalid mutation fixtures, Python semantic/replay/time guards and CLI; 25 tests PASS; see reports/T002_CONTRACTS.md; delivery verifies push |
| T003 | TODO — next | T002 | Local FastAPI server, health endpoint, read-only ADB device discovery with explicit unknown/unauthorized states; API tests and actual local smoke test |
| T004 | TODO | T003 | React dashboard connected to health/device endpoints, real empty/error states, Windows startup commands; API integration and browser validation |
| T005 | TODO | T002 | Kotlin/Compose app, navigation, Room entities/migrations, local repository; reproducible build, persistence tests and launch evidence |
| T006 | TODO | T005 | Screenshot/video import, provenance hashes, manual outcome review, round validation and history; reference-to-history tests with explicit fixtures |
| T007 | TODO | T006 | Empirical/Dirichlet estimates, uncertainty and immutable pre-result predictions; normalization, posterior, abstention and leakage tests |
| T008 | TODO | T003,T005 | Explicit pairing, encrypted authenticated transport, revoke, manual address; unauthorized/expired credential and transport tests before LAN use |
| T009 | TODO | T006,T008 | Durable outbox, PC SQLite ingestion, acknowledgments, corrections/conflicts, retry/reconnect; interruption and duplicate delivery tests |
| T010 | TODO | T004,T007,T009 | Integrated slice: imported/manual Android observation -> Room -> PC sync -> PC estimate -> web history -> versioned Android result; actual cross-component tests, stale-result rejection and separate prediction records |
| T011 | TODO | T006 | Calibration, detection interfaces, evidence review queue and finite-state machine; annotated references, noisy/missing frame tests; no unsupported automatic recognition claim |
| T012 | TODO | T005,T011 | Consented MediaProjection foreground service, visible stop, lifecycle/backpressure/thermal controls; denial, revocation, rotation and physical-device tests |
| T013 | TODO | T009,T011,T012 | Automatic round collection and cross-device live view; measured recognition, final-result confirmation and no duplicate rounds on real recordings/device |
| T014 | TODO | T007,T010 | Log loss/Brier/calibration, chronological holdouts and champion/challenger evaluation; seeded independent-game negative control and leakage audit |
| T015 | TODO | T014 | Reproducible PC research jobs for dependencies, Hot/pool hypotheses and drift; multiple-testing controls, cancellation and baseline comparisons |
| T016 | TODO | T014,T015 | Model registry, checksum/compatibility checks, atomic phone install and rollback; interrupted transfer and rollback tests |
| T017 | TODO | T007; U02,U03,U11 evidence | Verified payout matrix, exact scenario enumeration, up-to-six selection risk simulator and fixed risk limits; deterministic settlement examples, unknown-input abstention |
| T018 | TODO | T003,T010,T015 | Resource monitoring, limits, Eco/Balanced/Performance modes and latency scheduler; measured CPU/memory budgets, idle behavior and optional acceleration benchmark |
| T019 | TODO | T009 | CSV/JSON exports, retention, deletion, backup/restore and migrations; round-trip integrity and private-data exclusion tests |
| T020 | TODO | T010,T013,T014,T016,T017,T018,T019 | Complete production screens, accessibility, diagnostics and Windows launcher; responsive UI, functioning controls and operating instructions |
| T021 | TODO | T020 | Physical-device + PC end-to-end reliability/performance report; offline days/reconnect, shutdown, capture termination, database recovery, latency and battery evidence |

## Dependency and scope rules

T003 is the next implementation task. T003 and T005 establish the shared service and standalone Android foundation using T002 contracts; T004 connects the web interface to actual services. T010 is the first hybrid integration gate. T006 supplies an offline reference-to-history slice before automatic recognition. These tasks reconcile both directives without pretending that OCR is possible without references.

Pairing and encryption must be complete before the server is made available on the LAN. Live screen navigation/capture requires its own visible user authorization. Game evidence gaps block recognition/payout acceptance, but do not block typed contracts, manual annotation, storage or the offline pipeline. Do not mark physical-device acceptance passed using emulators or synthetic fixtures.

## Coverage of the directives

| Requirement group | Tasks |
|---|---|
| Initial audit and research documentation | T001 |
| Domain, relational data, state validation | T002,T005,T006,T009,T011 |
| Offline import and automatic capture | T006,T011,T012,T013 |
| Secure discovery, pairing and synchronization | T003,T008,T009,T010 |
| Android production interfaces | T005,T006,T007,T012,T020 |
| Web live/history/device dashboards | T004,T010,T020 |
| Probability, locking and error feedback | T007,T010,T014 |
| Research, chronological evaluation, drift and hypotheses | T014,T015 |
| Model deployment, registry and rollback | T016 |
| Risk/strategy, simulation and limits | T017 |
| CPU/GPU monitoring and scheduling | T018 |
| Data ownership, corrections, exports and backups | T006,T009,T019 |
| Device reliability and measured performance | T012,T013,T021 |

No deadlines are invented. Update this board as acceptance evidence changes; split oversized tasks while preserving requirement coverage.
