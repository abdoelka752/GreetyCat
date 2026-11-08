# Human review synchronization (`sync-1`)

This version supplements gameplay v1; it does not redefine its outcome/evidence/prediction schemas. These are Android human-attested review revisions and reference metadata, not independently verified PC observations. No capture time, recognition confidence, live game phase or predictive skill is fabricated. Media bytes and predictions are not transferred in T009.

`POST /api/sync/v1/reviews` uses T008's native HTTPS listener and bearer credential. The JSON envelope has exactly:

- `schema_version`: `sync-1`
- `source_id`: a durable, random UUID for this Android workspace; no hardware identifier
- `event_id`: the review revision UUID
- `payload_json`: the frozen JSON text matching [review.schema.json](review.schema.json), at most 8192 UTF-8 bytes
- `payload_sha256`: lowercase SHA-256 of those **exact UTF-8 bytes**, including whitespace/property order

The request body is capped at 16384 bytes. UUIDs are canonical lowercase versions 1-5/variant RFC4122; actual app IDs are random v4. UTC timestamps are valid calendar instants with exactly millisecond precision. Modes are BASIC/PROFESSIONAL. Provenance REAL/SYNTHETIC remains explicit and isolated. The [fixture](fixtures/review.synthetic.json) is synthetic metadata with an illustrative hash, not a real observation or media asset.

`evidence.imported_at` is local import time, never inferred capture time. `observed_at` and outcome are the human's claim; `confirmed_at` is local confirmation time. Both observation and import must precede/equal confirmation. Images have null video fields; video offset is inside the positive duration. Reference identity/hash/metadata cannot change, while an individual review's video offset may change. Revision 1 has no predecessor; each subsequent revision names the immediately previous revision of the same round and cannot move confirmation time backwards. Reason/reviewer/provenance/lineage remain in the immutable original payload.

PC isolation is `(authenticated pairing ID, workspace source ID)`. A different pairing or PC gets its own copy; acknowledgment from one destination never acknowledges another. Re-pairing does not claim/merge the old pairing's dataset. Old data remains on the PC after revocation; dataset ownership transfer/retention is future work.

HTTP 200 acknowledges a **committed** event with `schema_version`, `event_id`, `payload_sha256` and `status: STORED | REPLAY`. Repeating identical event ID/bytes in a namespace is REPLAY. Different bytes under the same ID conflict even if semantically equivalent JSON; the frozen sender representation must be replayed unchanged. Android checks identity/digest before recording its receipt. Losing the HTTP response after PC commit is safe: the durable Android event retries and receives REPLAY.

HTTP 409 returns those identity/digest fields, `status: CONFLICT`, a fixed `code` and `retryable` boolean. Codes: EVENT_CONFLICT, ROUND_IDENTITY_CONFLICT, ROUND_NUMBER_CONFLICT, REVISION_CONFLICT, CORRECTION_TIME_CONFLICT, EVIDENCE_IDENTITY_CONFLICT, MISSING_PREDECESSOR, STORAGE_LIMIT. The last two are retryable. Validation failure after a valid envelope returns HTTP 422 with the same fields and permanent INVALID_REVIEW. Invalid envelopes use the service's sanitized validation error. Revoked/expired/missing authorization returns HTTP 401. No negative response acknowledges an event.

PC commits a round identity, raw event and normalized review atomically. Changes cannot replace a human correction. Rejected identity/revision conflicts are recorded by ID, attempted digest, code and receive time; bounded audit capacity does not relax rejection. The latest review is derived from revisions, never overwritten. Evidence originals are absent, so a matching metadata hash alone is not independent evidence validation.

Android writes the review/outbox together. Existing reviews are backfilled in batches of at most 100 before an explicit sync action. Each action attempts at most 25 events and checks a 30-second action budget between requests; individual transport timeouts still apply. Retry delay doubles from 5 seconds to a 5-minute cap and persists; another explicit action after the delay resumes it. There is no automatic background scheduler in this checkpoint. Conflict receipts remain visible and are not ACKED or silently merged.

The Android immutable archive caps at 10000 events; when full, new local reviews still save and remain visibly not queued. PC caps at 100000 events and 1000 conflict-audit records. Acknowledged archives are retained, so these are lifetime limits until explicit retention/ownership tooling in T019. No automatic dropping/pruning/forensic erasure is claimed. Clearing demo rounds retains its explicitly synthetic outbox snapshots, separately from real history.
