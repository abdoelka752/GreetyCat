# Shared contracts v1.0

This directory implements the T002 wire-format boundary. JSON Schema Draft 2020-12 files define structure; `backend/greedycat/contracts.py` is the executable Python reference for structural **and semantic** rules. All references resolve from a local registry without HTTP retrieval. Android must run these fixtures plus the semantic rules when its implementation arrives at T005/T009. Android conformance is **NOT TESTED**.

## Windows commands

From repository root, with Python 3.13 available:

```powershell
python -m venv .venv
.\.venv\Scripts\python.exe -m pip install -r backend/requirements-contracts.txt
Push-Location backend
..\.venv\Scripts\python.exe -m unittest discover -s tests -v
..\.venv\Scripts\python.exe -m greedycat.contracts event ../contracts/fixtures/event-snapshot.json
Pop-Location
```

The CLI exits 0 for valid records, 1 with a versioned contract error for invalid records, and 2 for file/usage errors. No device, server or network connection is opened by validation. Dependency installation requires package access once; validation then works offline. The exact dependency set was installed and tested on CPython 3.13.4/Windows; other Python versions are not certified by this task.

## Wire and compatibility rules

- Required `schema_version` is the string `1.0`. Unknown versions/properties/enums fail closed; never discard unknown fields silently. Negotiate a supported version before synchronization. Changes to allowed fields or semantics require a new contract version and matching conformance tests.
- Use lowercase canonical UUID strings and canonical UTC timestamps `YYYY-MM-DDTHH:mm:ss.sssZ`. Millisecond precision is required; offsets, local times, invalid dates and leap seconds are rejected. Round numbers are digit strings to preserve large identifiers; they are not globally unique. `context_id` scopes the installation/server/reset context; its real-world assignment remains U06.
- JSON must have unique property names and finite numbers. Sequence counters stay within the cross-platform safe integer range, start at zero per producer/capture session, and are assigned durably by the eventual outbox. Delivery order/sequence collision handling belongs to T009.
- Every record has explicit REAL or SYNTHETIC provenance; related records must match. Fixtures here are fabricated test records, identifiers, timestamps and placeholder hashes, **not real observations or actual files**. No fixture is training data. A hash's shape does not establish that an evidence file exists or is authentic.
- Unknown values use explicit nulls and missing-data flags, rather than guessed values. Unknown mode can be retained but cannot support confirmed outcomes or predictions.

## Record types

| Schema | Purpose |
|---|---|
| common | Stable identity, enums, timestamps, probability map and result-evidence references |
| round | Round snapshot/lifecycle, mode/context identity, nullable timestamps and result candidate |
| outcome | Detection with evidence, confidence and explicit human review metadata |
| prediction | Separately identified Android/PC estimate or abstention, version/cutoffs/expiry and honest skill state |
| event | Retry-safe envelope for round snapshot, outcome review or locked prediction |
| errors | Input-value-free error envelope with stable codes and JSON Pointer path |

V1.0 supports human-confirmed outcomes only. UNREVIEWED detections can be retained in RESULT_PENDING but cannot finalize a round. Automatic visual confirmation requires later calibrated evidence and a contract revision. Human review does not require the detector confidence to be high; it requires reviewer identity and a confirmation time. Corrections must later be separate auditable records; this task provides no overwrite API.

V1.0 deliberately supports `NO_PROVEN_EDGE` and `NOT_ESTIMATED` uncertainty only. It does not invent uncertainty bounds or model performance. A later interval/model-evaluation contract must include actual measurements before widening those states.

## Normative semantic rules beyond JSON Schema

1. Training cutoff <= features cutoff <= issued time < expiry. A null training cutoff means no observed training history. ESTIMATE requires usable data and exactly ten finite, nonnegative probabilities, each <= 1. Their sum must differ from 1 by at most 1e-9 (absolute tolerance). ABSTAINED has null probabilities and a nonblank reason.
2. Observation follows every referenced result frame; human confirmation follows observation. Result evidence refers to result-bearing frames, never unrelated earlier screenshots. Round start <= betting close <= observation <= confirmation <= completion whenever those values are known. Result frames must also follow known start and betting close. Final phases and human-confirmed outcomes imply each other; completion requires its timestamp.
3. Null round number/start/betting-close/outcome values require their missing flags; unknown mode requires MODE. A confirmed outcome cannot be marked missing. Open betting cannot contain a closing time or outcome candidate. Candidate outcomes require RESULT_PENDING; confirmed outcomes require RESULT_CONFIRMED/ROUND_COMPLETED.
4. Round, outcome, prediction and event identities must agree on round UUID, context, mode and provenance. Snapshot envelopes also agree on capture session and phase. Event timestamps cannot precede the contained record's known timestamps. Prediction events are allowed only during BETTING_OPEN; outcome-review event phase matches review status.
5. Before linking a prediction to later round metadata, validate its identity, issued time strictly before known betting close, and expiry at or before close. Issued time must be strictly before the earliest result evidence, including unreviewed result evidence. Confirmation delay never permits retrospective prediction creation.
6. Live acceptance additionally requires the intended active round to be BETTING_OPEN and issued <= mapped current time < expiry. The caller must map clocks conservatively, including uncertainty, before using this helper. This task does not estimate clock differences or trust incoming timestamps.
7. `compare_replay` returns REPLAY for the same ID and structurally equal parsed JSON (object order ignored, arrays ordered, numeric values compared by value); a changed event with the same ID raises EVENT_CONFLICT. A changed locked prediction with the same ID raises PREDICTION_IMMUTABLE. Distinct Android and PC predictions have distinct IDs. This is a comparison guard, not a canonical hash/signature format.

Fixtures: individual JSON records are valid examples. `fixtures/invalid-cases.json` contains portable base-file + JSON Pointer set/remove mutations with expected error codes; these cases exercise both schemas and semantic guards. The Python tests additionally exercise cross-record replay, identity, time boundaries and live expiry. Future clients must implement all normative rules, not only load the schemas.

## Enforcement limits

The guards are stateless. They do not persist locks, authenticate producers, validate actual frame contents, assign contexts, reconcile clocks, check training dataset membership or prove claimed timestamps. Database uniqueness, transactional compare-and-insert, append-only corrections, trusted cutoff calculation and signed model lineage remain later tasks. A new ID must not be used to evade the eventual source/model/round uniqueness policy. This task validates metadata assertions; it cannot prove the absence of hidden future information.

Official references consulted on 2026-11-07: [jsonschema schema validation](https://python-jsonschema.readthedocs.io/en/stable/validate/) for explicit format checking and schema validation, and [local registry/reference handling](https://python-jsonschema.readthedocs.io/en/stable/referencing/) for offline reference resolution. The UTC-millisecond checker is project-defined so validation does not depend on optional date-format packages.
