# Project status — 2026-11-08

## Current checkpoint

T009 explicit human-review sync acceptance complete: transactional durable Android outbox, authenticated PC SQLite copy, append-only corrections, conflict audit and retry-safe committed acknowledgments. Commit/push is the final checkpoint action; verify the remote hash before claiming publication. T008b was pushed as `1f7af91e9c094cf62fc919a56a6865ed61cd0818`. Default services remain loopback; private-IP native operation requires explicit opt-in and matching TLS trust. Repository-local Git settings select abdoelka752 and disable interactive GitHub prompts.

## Completed scope

- T001: empty-repository audit, preserved directives and provenance, machine audit, architecture, file plan and 21-task backlog.
- T002: six Draft 2020-12 schemas for common fields, event, round, outcome, prediction and errors.
- Five synthetic valid fixtures and 29 portable invalid mutation cases.
- Offline Python structural/semantic validator, error envelope and local-file CLI.
- Stateless guards for duplicate replay/conflict, changed prediction IDs, identity/provenance, timestamps, probability normalization and stale/wrong-round results.
- T003: FastAPI health/device endpoints, typed OpenAPI diagnostics, local-only access checks, fixed loopback launcher and Windows startup script.
- Bounded read-only ADB query, explicit diagnostic failures, per-session pseudonymous IDs and UNKNOWN capture/game capabilities.
- Pinned service/test dependencies and actual HTTP/ADB smoke tooling. No phone or game navigation/capture occurred.
- T004: React/TypeScript/Vite overview/device dashboard, same-origin static serving, runtime API checks, selected-device details and honest unknown/empty/error states.
- Manual refresh and optional visible-page polling; no overlapping requests or persistent stale success after a failed refresh.
- Windows build/start launcher, actual launcher/HTTP smoke, client tests and isolated browser QA. No hosted Site or remote dashboard was created; this is local-only software.
- T005: Kotlin/Compose offline overview/history/settings, explicit synthetic demo route, isolated real/demo Room files, transactional draft validation/replay/conflict guards, generated schemas and explicit 1->2 migration.
- Persisted compact-spacing setting, visible storage failure states, local icon/theme, pinned Android toolchain/wrapper and owned headless emulator test helpers. No game data is seeded in real history.
- T006: user-selected image/video import with private original bytes, detected media type, bounded size/duration and SHA-256 provenance; offline image/video preview and explicit human outcome review.
- Confirmed history, retained correction revisions with reason/reviewer/time/evidence, stale-edit rejection, explicit history contexts and tested Room 2->3 migration. Real and synthetic files/reviews remain separate.
- T007: empirical frequencies, symmetric Dirichlet posterior means/parameter SD, normalized predictive entropy and explicit conditional-model assumptions; empty history shows no probability table.
- Bounded evidence-verified snapshots use review revisions known before a cutoff, isolate mode/context/provenance and preserve lineage. Room v4 and SQL triggers protect immutable ANDROID abstention records across corrections, restarts and demo draft clearing. Natural archive identities cannot be reused within a context.
- Manual targets retain unknown game phase/start time and an explicit unverified-acquisition label/attestation. They always ABSTAIN; no live probability forecast is issued. Local deadline and clock-consistency checks are not trusted acquisition or authenticated time.
- T008a: separate loopback HTTPS pairing service with an explicit private identity, expiring one-use invitations, hashed persistent sessions, operator/self revocation and a backward-clock guard.
- T008b: Settings -> Connect your PC, explicit certificate SHA-256 confirmation, manual private IPv4 HTTPS address, pinned TLS 1.2/1.3 client, Keystore AES-GCM atomic credentials, probe/revoke and warned offline local forgetting. The pairing API alone supports explicit certificate-matched private-IP operation; diagnostic HTTP/dashboard stays loopback-only. No lasting user pairing is created by synthetic acceptance tests.
- T009: Room v5 frozen review outbox and per-PC-pairing receipts, atomic review/queue writes, historical backfill, bounded explicit sync with persisted backoff and visible pending/conflict/not-queued counts. sync-1 carries human-reviewed metadata/notes/hashes only. PC schema v1 atomically preserves namespace-scoped raw events, normalized round/review revisions and immutable conflict audit. Media, predictions, automatic/background collection and PC calculation/web integration are excluded.

## Validation evidence

Current [T009 report](reports/T009_REVIEW_SYNC.md): final build/test APKs and **27 JVM / 87 backend tests PASS**; lint **0 errors/14 existing warnings**. Phone API 34: **39 non-UI tests PASS**, update/launch PASS; actual USB and direct Wi-Fi tests with the final transport guard **PASS** for PC commit/lost ACK, phone DB reopen, replay, exactly two preserved review revisions and post-revocation rejection. API 36 emulator: **45 PASS/2 explicit phone-fixture skips**. New consent test verifies cancellation sends nothing and confirmation invokes the injected synthetic sender. One initial Wi-Fi helper shutdown timed out after transport passed; bounded cleanup fix and rerun passed. No capture, real-data sync, game navigation, firewall changes or real research-data clearing. Test fixtures/mappings/identities/listeners removed; owned emulator stopped. Full phone UI remains unverified.

Prior [T008b report](reports/T008B_ANDROID_PAIRING.md): 24 JVM, 78 backend and 31 non-UI phone checks PASS; actual USB/Wi-Fi certificate trust/expiry/revocation checks PASS; emulator 36 PASS/1 phone-fixture skip. Those pairing-only network cases were not claimed rerun in T009; the existing JVM/backend and encrypted credential/storage regressions pass in current suites.

Prior [T008a report](reports/T008A_PC_PAIRING.md): 74 backend tests PASS for the PC-only loopback foundation. T008b supplies native Android trust/storage and opt-in private-IP transport; T009 adds explicitly confirmed review sync to that native boundary. No live prediction claim follows from those checks.

See [T007 report](reports/T007_STATISTICS_PREDICTIONS.md): debug/test APK build **PASS**, JVM **20 tests PASS**, API 36 emulator **33 tests PASS**, API 34 physical phone **29 non-UI tests PASS**, app update/cold launch **PASS**. Lint **0 errors/13 upgrade advisories**. Tests cover posterior/normalization, abstention, immutable SQL/persistence, cutoff/correction/target leakage, limits and migration with prior review rows. The updated app is open on the phone under the owner's existing app-testing authorization; no gallery browsing or screen capture occurred.

Prior [T006 report](reports/T006_REFERENCE_REVIEW.md): 11 JVM, 21 emulator and 18 non-UI phone checks PASS, plus app installation/launch. Its full phone UI run stalled; T007 verifies the UI on the emulator and does not claim full phone UI completion.

Full phone UI automation remains **BLOCKED / NOT PASSED** after the T006 five-minute timeout. Emulator UI checks pass. System picker/provider interaction, video playback controls, actual game references, visual screenshots, other devices and Android versions remain **NOT TESTED**. Do not equate emulator or phone-storage coverage with complete physical-device acceptance.

Prior [T005 report](reports/T005_ANDROID_FOUNDATION.md): foundation build, 8 JVM and 12 emulator tests PASS. Its capture-consent rejection applies to the screenshot attempt recorded there; no new capture was attempted here. T008b adds INTERNET for explicit PC pairing; no capture, overlay, shared-storage or background-service permissions are declared.

Prior [T004 report](reports/T004_DASHBOARD.md): 56 Python, 9 client and 7 browser tests PASS, build/audit and actual Windows launch/HTTP/ADB smoke PASS. Current backend regressions pass in the 87-test suite; unchanged web client/browser suites were not rerun for T009. Documentation/provenance and staged whitespace checks pass before commit.

Full Android wire-contract conformance, verified live forecasts, trusted clock mapping, screen/game visibility, capture, real gameplay/model performance and hybrid gameplay end-to-end behavior: **NOT TESTED / NOT IMPLEMENTED**. Human outcome review and immutable local abstentions do not establish automatic recognition, authenticated evidence interpretation or demonstrated predictive skill.

## Next action

T010 after the T009 commit/push checkpoint: integrate synced human-reviewed history with a PC baseline, web history and versioned result returned to Android, preserving Android/PC prediction separation and stale-result rejection. T009 remains explicit foreground metadata sync. Background scheduling, full phone UI automation, multi-day reconnect, identity/ownership transfer, conflict repair/retention, API 26/other devices and long-term networking remain unverified. Android archive capacity is 10000 events and PC storage 100000 until T019 supplies retention; local reviews still save at capacity. No demonstrated predictive edge or verified payout matrix exists.

## Outstanding evidence

Original game rules/screenshots and a complete authorized round recording remain necessary for reliable recognition and payout validation. Software contracts, manual review, storage and baselines can proceed without them. Device discovery does not authorize navigation or capture.
