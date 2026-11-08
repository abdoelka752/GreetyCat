# Project status — 2026-11-08

## Current checkpoint

T005: Android foundation complete; build, persistence/migration/UI tests and emulator launch PASS. Commit/push is the final checkpoint action; verify the remote hash before claiming publication. T004 was pushed as `a469c5118c59affb7f37d716758294d44cc04dc5`. No tasks after T005 are complete. Repository-local Git settings select abdoelka752 and disable interactive GitHub prompts.

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

## Validation evidence

See [T005 report](reports/T005_ANDROID_FOUNDATION.md): Android debug/test APK build **PASS**, JVM **8 tests PASS**, API 36 emulator **12 tests PASS**, lint **0 errors/13 upgrade warnings**, app cold launch **PASS**. Migration runtime mismatch was fixed and its test passes. APK declares no network/capture/shared-storage permissions. Visual screenshot review **NOT TESTED**: automatic approval review required explicit capture consent. Physical devices and other Android versions **NOT TESTED**.

Prior [T004 report](reports/T004_DASHBOARD.md): **56 Python, 9 client and 7 browser tests PASS**, build/audit and actual Windows launch/HTTP/ADB smoke PASS. Those suites were not rerun for this Android-only source change. Documentation/provenance and staged whitespace checks are required before commit.

Full Android wire-contract conformance, outcome/prediction lock persistence, trusted clock mapping, screen/game visibility, capture, real gameplay/model performance and hybrid gameplay end-to-end behavior: **NOT TESTED / NOT IMPLEMENTED**. T005 checks draft invariants and one shared synthetic fixture; emulator results do not establish physical-device behavior.

## Next action

T006 after the T005 commit/push checkpoint: offline screenshot/video reference import, provenance hashes and manual outcome review. Preserve separate demo/real datasets; no automatic recognition or outcome confirmation from unverified references.

## Outstanding evidence

Original game rules/screenshots and a complete authorized round recording remain necessary for reliable recognition and payout validation. Software contracts, manual review, storage and baselines can proceed without them. Device discovery does not authorize navigation or capture.
