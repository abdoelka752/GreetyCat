# T002 — shared contracts validation

Date: 2026-11-07. Scope: versioned wire schemas and executable Python reference validation. No server, phone control, capture or prediction model was added.

## Delivered behavior

Events select a typed payload for round snapshots, outcome review or locked predictions. Related records must match identity, context, mode and real/synthetic provenance. Unknown schema versions, properties, invalid UUIDs/dates and ambiguous JSON fail validation. Outcomes retain raw confidence and result-evidence references; confirmation requires human review metadata. Unknown information stays null with missing-data flags.

Estimates require a normalized ten-outcome probability vector; abstentions require a reason and null probabilities. Training/feature cutoffs cannot follow issuance. Context checks reject prediction issuance at/after betting close or after the earliest known result frame. Live checks reject expired/wrong-phase results. Replay comparison accepts an unchanged record but rejects a changed event or locked prediction sharing an existing ID; Android and PC retain separate prediction IDs.

All five fixture records are synthetic. Placeholder evidence hashes do not identify actual screenshots. The uniform fixture distribution is an API example, not a model output or inferred game distribution. V1.0 supports NO_PROVEN_EDGE and NOT_ESTIMATED uncertainty only.

## Commands and results

Environment: Windows, CPython 3.13.4, project-local `.venv`. Pinned dependencies are listed in `backend/requirements-contracts.txt`.

| Check | Command | Result |
|---|---|---|
| Contract suite (from backend/) | `..\.venv\Scripts\python.exe -m unittest discover -s tests -v` | PASS: 25 tests; includes 29 invalid mutation cases and valid fixture/schema checks |
| CLI (tested through subprocesses) | `python -m greedycat.contracts event <file>` | PASS: valid=0, invalid JSON=1, missing file=2 |
| Dependencies (root) | `.\.venv\Scripts\python.exe -m pip check` | PASS: no broken requirements |
| Documentation (root) | `python tools/verify_project_docs.py` | PASS: required docs, task coverage, local links and preserved directive hashes |

Tests also cover null/empty history abstention shape, all ten regular/special outcome labels, unreviewed detection remaining pending, mode/context mismatch, result frames from earlier rounds, malformed/future/contradictory times, finite-number checks, duplicated JSON properties, probability-sum tolerance, review evidence uniqueness, stale response boundary, late confirmation leakage, and prediction mutation. Socket creation is blocked during valid-fixture validation to check offline resolution. The CLI executes the validator in fresh processes.

## Review decisions and limits

The Python package lives at `backend/greedycat/`, with schemas in repository-level `contracts/v1/`; no packaging framework or HTTP stack is needed for this task. T003 can package this layout when introducing the server. Shared contract schemas were checked against the Draft 2020-12 meta-schema by the validator before use.

JSON Schema cannot enforce timestamp comparisons, sums, immutability or relations between records. Those rules are normative in `contracts/README.md` and implemented by Python guards. Future Kotlin implementations must run the same shared fixtures and matching cross-record checks; Android interoperability is **NOT TESTED**.

The replay and lock checks are stateless comparisons. Transactional durable locking/uniqueness, corrections, retry acknowledgments, authenticated producer identity, clock synchronization, dataset lineage and actual evidence authenticity are **NOT IMPLEMENTED** here. Metadata validation cannot prove that a producer did not use undisclosed future information. No recognition accuracy, predictive edge or end-to-end performance is claimed.

Current official sources consulted: [jsonschema validation and explicit format checking](https://python-jsonschema.readthedocs.io/en/stable/validate/), [registry-based reference resolution](https://python-jsonschema.readthedocs.io/en/stable/referencing/), and [maintainer package metadata](https://pypi.org/project/jsonschema/). Installed and tested jsonschema version: 4.26.0. Date parsing is a project-defined canonical UTC-millisecond format checker.

Next task: T003, local health service and real, read-only device discovery. The final Git checkpoint is created only after applicable verification and staged-diff review; the delivery message records the pushed hash.
