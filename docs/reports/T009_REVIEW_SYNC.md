# T009: durable human-review synchronization

T008b was pushed as `1f7af91e9c094cf62fc919a56a6865ed61cd0818`. This checkpoint adds a real Android -> authenticated PC storage path for human-reviewed outcomes and corrections. It does not add capture, automatic recognition, PC predictions or web gameplay history; T010 remains the first integrated gameplay calculation/dashboard/return path.

## Acceptance and scope

Reviews must save offline. Their delivery queue must survive restarts and failure after PC commit but before local acknowledgment. Retries must not duplicate rows or replace corrections. Invalid/unauthorized/conflicting delivery must stay unacknowledged and visible. Older reviews require safe backfill. New code must preserve prior Room data and immutable prediction guards. Actual cross-device checks use explicitly synthetic isolated records only, with no game navigation, media capture, real-data transmission or deletion.

## Implementation

- Android 0.9.0/code 5, Room schema 5 adds sync identity, immutable outbox and per-destination receipts through an explicit 4->5 migration. Prior schemas remain unchanged. Confirming a human review and queuing its frozen JSON/hash is one Room transaction. This also applies to correction revisions; prediction records are not modified or synchronized.
- A random workspace source UUID is durable and unrelated to hardware. Frozen event ID equals the review revision ID. Payload includes context/mode/provenance/round number, lineage/value/human timestamps/reviewer/notes and reference ID/hash/import metadata/video position. It excludes media bytes, private paths/URIs, device IDs, pairing tokens, confidence inventions and inferred capture times.
- The new [sync-1 contract](../../contracts/sync-v1/README.md) intentionally supplements gameplay v1: imported review metadata is not proof of PC-observed game evidence. Exact UTF-8 payload bytes are SHA-256 checked. Structural/semantic validation rejects invalid dates, identity changes, missing lineage, changed reference metadata and out-of-range video positions. No fabricated certainty or live phase is attached.
- Explicit Settings -> Connect your PC -> Sync reviewed history shows a confirmation explaining notes/hashes and retained local media. Sync uses the existing pinned authenticated TLS boundary; no automatic/background transfer or new dependency/permission. Each action backfills at most 100 unqueued historical revisions, attempts at most 25 records and checks a 30-second action budget between requests. Transport timeouts apply to an in-flight request. Pending work is durable; manual retry respects persistent 5-second exponential backoff capped at 5 minutes. Clock rollback cannot create an unbounded retry delay.
- PC `research.sqlite3` sits beside the private pairing ledger. Its schema v1 has normalized round/review identities, raw immutable events and bounded conflict audit. Event/round/review insertion is atomic. Repeating a committed event returns REPLAY; a lost response therefore needs no special destructive recovery. SQL triggers reject updates/deletions. Correction chains remain append-only and monotonic; missing predecessors can be retried while later queue work progresses. Permanent conflicts stay visible and are never ACKED or silently merged. There is no merge/repair UI in this checkpoint.
- A PC namespace is authenticated pairing ID plus Android source UUID. Local receipts use certificate fingerprint plus pairing ID. New pairing/PC receives a separate copy and cannot reuse old acknowledgment state; it does not automatically merge or take ownership of a former dataset. Revocation blocks subsequent ingestion without deleting the PC history. Ownership transfer and retention are future work.
- Android archive capacity is 10000 events; when full, local review saving continues and the missing queue count remains visible. Acknowledged snapshots are retained until future explicit retention tooling. Demo clearing also retains queued synthetic snapshots; UI discloses this. PC caps at 100000 stored events and 1000 conflict-audit entries; it still rejects conflicts if the audit cap is reached. These lifetime limits are documented, not silent pruning policies.
- The native API alone accepts `POST /api/sync/v1/reviews` (16384-byte request cap; payload <=8192 UTF-8 bytes). Pairing endpoints retain their 4096-byte limit. Acknowledgments bind event ID/digest and are checked before local receipt updates. Invalid/permanent conflicts, missing predecessors, storage limits and auth failure have distinct safe handling. Original diagnostic HTTP/dashboard stays loopback-only.

## Verification

**PASS**: backend regression suite, 87 tests, 20.743s. Nine new sync cases cover committed replay across reopen, append-only correction/stale branches, out-of-order retry, event/round/evidence conflicts, pairing isolation, injected transaction failure without orphan rows, invalid checksum/duplicate JSON/calendar/media metadata, capacity/audit limits and authorized HTTP rejection/revocation. Existing diagnostic/dashboard/contract/security checks also passed; no dependency changes, `pip check` PASS.

**PASS**: initial full Android debug/test APK, JVM and lint build, 5m17s; 26 JVM tests. Final acknowledgment review added a strict HTTP-status/body agreement guard and a regression case: HTTP 409/422/500 can never become an acknowledgment by claiming STORED/REPLAY in the body. Final full build PASS, 5m15s; JVM 27 with zero failures. Lint stays 0 errors/14 warnings (13 existing upgrade advisories and the retained custom trust-manager review advisory).

**PASS**: physical API 34 phone, 39 non-UI tests, 11.302s; tests include Room 4->5 migration/guard preservation, atomic review/correction queue, immutable archive surviving demo clearing, lost ACK plus DB reopen, interrupted delivery, historical backfill, per-pairing receipts, durable conflict state, 25-event limit/cooldown and capacity without local-review loss. App update/cold launch PASS, one observed cold launch 2103ms (not a benchmark). These storage checks preceded the final transport-only acknowledgment guard; unchanged storage behavior was not needlessly rerun.

**PASS**: real physical-phone USB and direct Wi-Fi sync integration. Native reviewed synthetic PNG -> Room review+correction/outbox -> authenticated PC SQLite -> deliberately discarded response after actual commit -> phone DB close/reopen -> replay/acknowledgment -> correction preserved -> credential revocation denies ingestion. The PC helper independently verifies exactly two rows with CORN then TOMATO; no duplicate original. Neither case skips. Both transports were rechecked successfully using the final transport-guard APK; no lasting real pairing or research-data transmission was created.

The first Wi-Fi run passed its data checks but its temporary-server shutdown exceeded ten seconds, so the command failed overall. The helper now bounds graceful shutdown, disables long test keepalive and has a bounded force-exit fallback; the Wi-Fi rerun and cleanup passed. This was a test-harness cleanup failure, not counted as a passing run. No firewall settings were changed, and private fixtures/owned mappings/listeners were removed.

**PASS with explicit skips**: API 36 owned emulator `OK (47 tests)`, 89.511s: 45 passed, two phone-fixture-dependent integration cases skipped with status -4. The sync integration case passed on the physical phone over both transports; T008 pairing-only integration is not claimed rerun here. New Compose consent test proves Cancel sends nothing and explicit Sync reviews invokes the injected test transport; it is a synthetic UI test, not a phone UI/network claim. Existing UI/storage/prediction tests pass. Owned emulator stopped afterwards.

Final APK: 12,271,114 bytes; SHA-256 `84deb860b4664b5ceb726512221b08988b1779cea2074afb1ac53cf82b4e3805`. Room schema 5 is exported/tracked, schemas 1-4 unchanged. Runtime APKs, media, private data and evidence logs remain ignored. Documentation/provenance/links and reviewed whitespace are checked before commit.

**NOT TESTED**: full physical-phone UI automation (prior T006 timeout unresolved), manual real-data sync, multi-day reconnect, power-loss/filesystem corruption, transport benchmarks, background execution, other devices/API levels and ownership/retention/merge recovery. No actual game references, live observations, prediction edge or PC calculation/web/result-return pipeline was validated by T009.

## Use and limits

Start the existing native HTTPS service using the [pairing instructions](T008B_ANDROID_PAIRING.md), then pair on the phone. New confirmed reviews queue locally without network. To send them, open Settings -> Connect your PC -> Sync reviewed history and confirm. Counters distinguish acknowledged, pending, conflicts and not-yet-queued records. Repeat after the retry delay or to drain another bounded batch; keep the PC available. Do not interpret a successful pairing probe as successful data synchronization.

Old reviews are backfilled on explicit sync, not during a potentially long database upgrade. If a correction reaches the PC before its predecessor, the sender retains it for retry; no revision is rewritten to make it fit. Notes may contain personal content supplied by the user, which is why transmission requires explicit confirmation. The PC keeps those notes in its private research database. Original files never leave the phone in this task, and PC metadata hashes do not independently authenticate image interpretation.

If the PC is offline, the local records/queue remain. After an expired/revoked session, re-pairing starts an independent namespace; old PC copies and acknowledgments are not adopted automatically. Conflicts require explicit future data-review/repair tooling rather than a force-overwrite button. Long multi-day reconnect, automatic scheduling, deletions/ownership transfer, background execution, performance/battery benchmarks, other Android versions and full physical-phone UI remain unverified or deferred. T019 must supply retention before lifetime archive limits can be reclaimed. No claim of unlimited storage, exactly-once HTTP delivery, predictive advantage or fully autonomous collection is made.

Test commands:

```powershell
Push-Location backend
..\.venv\Scripts\python.exe -m unittest discover -s tests -v
Pop-Location
Push-Location android
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug :app:assembleDebugAndroidTest --no-daemon --console=plain
Pop-Location
.\tools\test-android-phone.ps1 -RunInstrumentation -NonUiOnly
.\.venv\Scripts\python.exe tools/test_pairing_phone.py --sync
.\.venv\Scripts\python.exe tools/test_pairing_phone.py --sync --lan-address PRIVATE_IP
```

Phone commands require the owner's app-testing authorization. The helper installs only this app/test APK, uses app-private fixtures and temporary PC identities, removes owned forwarding/fixtures/listeners, revokes synthetic authority and reopens this app. Device IDs, private addresses, captures, runtime databases, keys, APKs and logs are excluded from Git. Official guidance consulted: [Android offline-first data layers](https://developer.android.com/topic/architecture/data-layer/offline-first), [Room transactions and migration behavior](https://developer.android.com/jetpack/androidx/releases/room). Existing pinned dependencies remain unchanged.
