# Project status — 2026-11-07

## Current checkpoint

T003: local health service and read-only device discovery complete; local checks PASS. Commit/push is the final checkpoint action; verify the remote hash before claiming publication. T002 was pushed as `01f13a59ccdf3197df03f4481fb228f0ccde2c84`; T001 as `f570faa656ac851a7811211ec8ed5bbaa3c34a74`. No tasks after T003 are complete.

## Completed scope

- T001: empty-repository audit, preserved directives and provenance, machine audit, architecture, file plan and 21-task backlog.
- T002: six Draft 2020-12 schemas for common fields, event, round, outcome, prediction and errors.
- Five synthetic valid fixtures and 29 portable invalid mutation cases.
- Offline Python structural/semantic validator, error envelope and local-file CLI.
- Stateless guards for duplicate replay/conflict, changed prediction IDs, identity/provenance, timestamps, probability normalization and stale/wrong-round results.
- T003: FastAPI health/device endpoints, typed OpenAPI diagnostics, local-only access checks, fixed loopback launcher and Windows startup script.
- Bounded read-only ADB query, explicit diagnostic failures, per-session pseudonymous IDs and UNKNOWN capture/game capabilities.
- Pinned service/test dependencies and actual HTTP/ADB smoke tooling. No phone or game navigation/capture occurred.

## Validation evidence

See [T003 report](reports/T003_LOCAL_SERVER.md) and the earlier [T002 report](reports/T002_CONTRACTS.md). Full Python suite: **PASS, 52 tests** (25 contracts, 14 discovery, 13 API/launcher). Actual loopback HTTP + installed ADB smoke: **PASS**, one AVAILABLE transport. Dependency consistency, documentation/provenance and PowerShell syntax checks: **PASS**. Staged diff review/whitespace checks are required before commit.

Android conformance, database-backed immutability, trusted clock mapping, dashboard, screen/game visibility, capture, real gameplay/model performance and hybrid end-to-end behavior: **NOT TESTED / NOT IMPLEMENTED**. ADB transport discovery is not device/app/capture validation.

## Next action

T004: implement the functional React/TypeScript dashboard connected to the T003 health and device APIs, real empty/error states and Windows startup commands. Keep unavailable capabilities visibly unknown. Integrate through a controlled same-origin proxy/serving arrangement compatible with T003 Host/Origin checks; test both API flow and browser behavior before marking complete.

## Outstanding evidence

Original game rules/screenshots and a complete authorized round recording remain necessary for reliable recognition and payout validation. Software contracts, manual review, storage and baselines can proceed without them. Device discovery does not authorize navigation or capture.
