# Project status — 2026-11-07

## Current checkpoint

T002: shared contracts and Python reference validation complete; local checks PASS. Commit/push is the final checkpoint action; verify the remote hash before claiming publication. T001 was pushed as `f570faa656ac851a7811211ec8ed5bbaa3c34a74`. No tasks after T002 are complete.

## Completed scope

- T001: empty-repository audit, preserved directives and provenance, machine audit, architecture, file plan and 21-task backlog.
- T002: six Draft 2020-12 schemas for common fields, event, round, outcome, prediction and errors.
- Five synthetic valid fixtures and 29 portable invalid mutation cases.
- Offline Python structural/semantic validator, error envelope and local-file CLI.
- Stateless guards for duplicate replay/conflict, changed prediction IDs, identity/provenance, timestamps, probability normalization and stale/wrong-round results.
- Pinned contract dependency set in a project-local ignored virtual environment. No phone or game navigation occurred.

## Validation evidence

See [T002 report](reports/T002_CONTRACTS.md) for commands, scope and limitations. Python contract suite: **PASS, 25 tests**, including the 29 shared invalid cases. Dependency consistency and documentation/provenance checks: **PASS**. Staged diff review/whitespace checks are required before commit.

Android conformance, database-backed immutability, trusted clock mapping, backend HTTP service, dashboard, capture, real gameplay/model performance and hybrid end-to-end behavior: **NOT TESTED / NOT IMPLEMENTED**.

## Next action

T003: add the loopback-bound local FastAPI service with a health endpoint and bounded read-only ADB discovery. Show missing/unauthorized/offline/available states without inferring that a connected phone is capturing or showing Greedy Cat. Add API/diagnostic tests and execute a real local smoke test. Consult current official dependency/API documentation and reuse T002 errors/contracts where applicable.

## Outstanding evidence

Original game rules/screenshots and a complete authorized round recording remain necessary for reliable recognition and payout validation. Software contracts, manual review, storage and baselines can proceed without them. Device discovery does not authorize navigation or capture.
