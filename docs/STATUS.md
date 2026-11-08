# Project status — 2026-11-08

## Current checkpoint

T008a: PC pairing/TLS checkpoint complete; 74 backend checks PASS. Commit/push is the final checkpoint action; verify the remote hash before claiming publication. T007 was pushed as `11cfbd05e494b6012eee1a0ad9ca0abcaafd9019`. T008 is split into independently reviewable PC (T008a) and Android/cross-device (T008b) checkpoints; the parent remains incomplete. Repository-local Git settings select abdoelka752 and disable interactive GitHub prompts.

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
- T008a: separate loopback HTTPS pairing service with an explicit private identity, expiring one-use invitations, hashed persistent sessions, operator/self revocation and a backward-clock guard. T008b must supply Android trust/storage/UI and cross-device acceptance before LAN operation.

## Validation evidence

Current [T008a report](reports/T008A_PC_PAIRING.md): **74 backend tests PASS**, including 18 pairing tests and an actual temporary HTTPS listener. One-use/expiring invitations, hashed persistent sessions, revocation, concurrency, clock-rollback guard, request boundaries, certificate trust/substitution/hostname rejection and regression checks passed. `pip check` PASS. Both services remain loopback-only; no Android source, APK or phone interaction changed in this checkpoint. Android certificate trust/secure storage/UI, cross-device connectivity and LAN acceptance remain **NOT IMPLEMENTED / NOT TESTED** in T008b. PC-only pairing does not complete T008.

See [T007 report](reports/T007_STATISTICS_PREDICTIONS.md): debug/test APK build **PASS**, JVM **20 tests PASS**, API 36 emulator **33 tests PASS**, API 34 physical phone **29 non-UI tests PASS**, app update/cold launch **PASS**. Lint **0 errors/13 upgrade advisories**. Tests cover posterior/normalization, abstention, immutable SQL/persistence, cutoff/correction/target leakage, limits and migration with prior review rows. The updated app is open on the phone under the owner's existing app-testing authorization; no gallery browsing or screen capture occurred.

Prior [T006 report](reports/T006_REFERENCE_REVIEW.md): 11 JVM, 21 emulator and 18 non-UI phone checks PASS, plus app installation/launch. Its full phone UI run stalled; T007 verifies the UI on the emulator and does not claim full phone UI completion.

Full phone UI automation remains **BLOCKED / NOT PASSED** after the T006 five-minute timeout. Emulator UI checks pass. System picker/provider interaction, video playback controls, actual game references, visual screenshots, other devices and Android versions remain **NOT TESTED**. Do not equate emulator or phone-storage coverage with complete physical-device acceptance.

Prior [T005 report](reports/T005_ANDROID_FOUNDATION.md): foundation build, 8 JVM and 12 emulator tests PASS. Its capture-consent rejection applies to the screenshot attempt recorded there; no new screen capture was attempted in T006. APK permissions remain limited to the app-scoped signature receiver permission.

Prior [T004 report](reports/T004_DASHBOARD.md): **56 Python, 9 client and 7 browser tests PASS**, build/audit and actual Windows launch/HTTP/ADB smoke PASS. Those suites were not rerun for this Android-only source change. Documentation/provenance and staged whitespace checks are required before commit.

Full Android wire-contract conformance, verified live forecasts, trusted clock mapping, screen/game visibility, capture, real gameplay/model performance and hybrid gameplay end-to-end behavior: **NOT TESTED / NOT IMPLEMENTED**. Human outcome review and immutable local abstentions do not establish automatic recognition, authenticated evidence interpretation or demonstrated predictive skill.

## Next action

T008b after the T008a commit/push checkpoint: Android manual-address pairing screen, operator-confirmed certificate trust before credential transmission, secure token storage, probe/revoke/reconnect and actual cross-device tests. The services stay loopback-only until secure pairing/transport acceptance passes. No demonstrated predictive edge or verified payout matrix exists.

## Outstanding evidence

Original game rules/screenshots and a complete authorized round recording remain necessary for reliable recognition and payout validation. Software contracts, manual review, storage and baselines can proceed without them. Device discovery does not authorize navigation or capture.
