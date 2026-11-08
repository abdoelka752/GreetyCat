# Task board

Each row is a reviewable task with a commit and push after passing its acceptance checks. TODO is not implementation. T007 is the current checkpoint; publication evidence is recorded in the delivery message and subsequent status checkpoint.

| Task | Status | Dependencies | Deliverable and completion evidence |
|---|---|---|---|
| T001 | DONE — pushed f570faa | none | Audit, directives, architecture, task board and validation plan; checks PASS; remote main matched f570faa656ac851a7811211ec8ed5bbaa3c34a74 |
| T002 | DONE — pushed 01f13a5 | T001 | Six v1 schemas, fixtures, Python integrity guards and CLI; 25 tests PASS; remote matched 01f13a59ccdf3197df03f4481fb228f0ccde2c84 |
| T003 | DONE — pushed e7e18ab | T002 | Local FastAPI/ADB diagnostics; 52 tests and actual HTTP/ADB smoke PASS; remote matched e7e18abe8b9abf4b871349e2f78cbf199be6bfc0 |
| T004 | DONE - pushed a469c51 | T003 | React overview/device dashboard on real APIs; same-origin serving; refresh/selection/polling and honest empty/error states; 56 Python, 9 client, 7 browser tests and Windows launcher smoke PASS; reports/T004_DASHBOARD.md; remote main matched a469c5118c59affb7f37d716758294d44cc04dc5 |
| T005 | DONE - pushed 409963f | T002 | Kotlin/Compose offline foundation, isolated real/demo Room repositories, schema migration and navigation; build, 8 JVM and 12 emulator tests PASS; lint 0 errors/13 warnings; reports/T005_ANDROID_FOUNDATION.md; remote main matched 409963f4d71e84a69469433fd4454d5842df979c |
| T006 | DONE - pushed 4d925c4 | T005 | Bounded private image/video import, SHA-256 provenance, attested outcome review, append-only corrections and context/history; 11 JVM and 21 emulator tests PASS, 18 non-UI phone tests PASS and app launch PASS; phone UI automation incomplete; reports/T006_REFERENCE_REVIEW.md; remote main matched 4d925c43e58dbec1464c70346061829f81fdf250 |
| T007 | DONE - local checks PASS | T006 | Empirical/Dirichlet research means, SD/entropy, bounded as-of evidence snapshots and immutable prospective manual abstentions; 20 JVM, 33 emulator and 29 non-UI phone tests PASS; migration/SQL protection/replay/leakage checks PASS; verified live forecasts remain gated; reports/T007_STATISTICS_PREDICTIONS.md; delivery verifies push |
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

T005 establishes the standalone Android foundation using a subset of T002 draft invariants. T006 adds offline reference review; T008 is next after the T007 commit/push checkpoint. T004 now connects the diagnostic dashboard to the actual T003 service. T010 remains the first hybrid gameplay integration gate. T006 supplies an offline reference-to-history slice before automatic recognition. These tasks reconcile both directives without pretending that OCR is possible without references.

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
