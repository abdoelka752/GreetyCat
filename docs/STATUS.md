# Project status — 2026-11-07

## Current checkpoint

T004: functional local React diagnostic dashboard complete; local checks PASS. Commit/push is the final checkpoint action; verify the remote hash before claiming publication. T003 was pushed as `e7e18abe8b9abf4b871349e2f78cbf199be6bfc0`. No tasks after T004 are complete. Repository-local Git settings select abdoelka752 and disable interactive GitHub prompts; verified dry-run push succeeded after that configuration change.

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

## Validation evidence

See [T004 report](reports/T004_DASHBOARD.md). Full Python suite: **PASS, 56 tests**; client suite: **PASS, 9 tests**; browser suite: **PASS, 7 tests**. Typecheck/build, runtime dependency audit, Python dependency consistency and actual Windows build/start + HTTP/ADB smoke: **PASS**. Latest actual device list was valid and empty. Desktop and phone viewport screenshots were inspected. Documentation/provenance and staged whitespace checks are required before commit.

Android conformance, database-backed immutability, trusted clock mapping, screen/game visibility, capture, real gameplay/model performance and hybrid gameplay end-to-end behavior: **NOT TESTED / NOT IMPLEMENTED**. Browser mobile viewport validation is not Android device validation.

## Next action

T005: create the Kotlin/Compose Android foundation, navigation/theme, Room schema/repository/migrations and meaningful persistence tests. Check current official Android documentation and actual SDK/Gradle/JDK compatibility before pinning versions. Build and launch on an available emulator/device with evidence; do not infer capture permission from ADB authorization or manufacture live gameplay data.

## Outstanding evidence

Original game rules/screenshots and a complete authorized round recording remain necessary for reliable recognition and payout validation. Software contracts, manual review, storage and baselines can proceed without them. Device discovery does not authorize navigation or capture.
