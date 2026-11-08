# T006 reference import and human review — 2026-11-08

## Acceptance and scope

Select an image/video through Android's system document picker; copy its original bytes privately with SHA-256 provenance; preview it offline; enter and validate the observed mode, round, result and UTC observation time; explicitly attest and confirm; show the confirmed result in local history. A correction appends reviewer/time/reason/evidence and links to the prior revision. No result is inferred automatically. Synthetic tests stay outside the real workspace.

Supported inputs: PNG, JPEG, WebP, MP4 and WebM, at most 128 MiB each. Images have a 40-megapixel bounds limit and sampled previews. Videos require a visual track and duration between 1 ms and two hours. Video review includes a validated position inside the recording. File signatures and platform media parsers validate type; source names, source paths and provider URIs are not stored. Import time and observed time remain distinct.

Each workspace has a persisted context and local reviewer pseudonym. Context changes require an explicit dialog and retain old history. Same-context/mode/round duplicates are rejected transactionally. A round's original pending draft stays unchanged; confirmation is represented by a separate immutable review chain. History derives its human-confirmed status from that chain, not from invented lifecycle timestamps. No outcome JSON export or full wire-contract conformance is claimed.

## Data and implementation

Room v3 adds `reference_files`, `review_identity` and `outcome_reviews`. The explicit 2->3 migration preserves draft rows and appearance settings. Schemas v1/v2 remain unchanged; generated v3 is tracked. Review revisions have unique round/revision and predecessor indexes plus evidence/round foreign keys. A correction must name the current predecessor; stale changes are rejected. Evidence bytes are rehashed before confirmation.

The importer uses bounded 64 KiB stream buffers and cancellable work on Dispatchers.IO. A temporary copy is renamed after validation; copies interrupted by stream failure/cooperative cancellation and invalid media are removed. A process kill can leave an unreferenced partial file. Duplicate imports reuse the original evidence identity. Demo and real files, metadata and reviews remain separate. A failed metadata transaction after a completed file rename can leave an unreferenced private file; automatic orphan cleanup and retention belong to T019. No destructive migration or automatic evidence deletion is introduced.

## Verification

Final build: **PASS**, 3m 52s; JVM: **PASS, 11 tests**, zero failures/errors/skips; lint: **PASS, 0 errors / 13 warnings** (5 dependency updates, 5 newer-version notices, 2 AGP notices, 1 target-SDK advisory). Dependency/target upgrades remain visible. APK permission inspection: **PASS**, only the app-scoped signature receiver permission; no network, capture or shared-storage permissions.

Debug APK: 12,201,778 bytes; SHA-256 `eddfe2d1723da4e37f4689376eccec9ecc50060a4e906624a9e5b81ecb45a4a3`.

Physical-phone app install and cold launch: **PASS**, API 34, MainActivity `Status: ok`, TotalTime 2707 ms. This is one launch, not a performance benchmark. The first test-APK installation was rejected by Android with `INSTALL_FAILED_USER_RESTRICTED`; the owner approved a retry, which installed successfully. The initial phone run stalled in a Compose UI test and was stopped by targeting only this app; it is not counted as a passing run. A separate 18-test non-UI run passed 17 checks and exposed a synthetic-fixture hardware-encoder configuration failure. Fixture generation was changed to the Android software AVC encoder with aligned 320x240 dimensions. The UI fixture now uses the normal MainActivity. The corrected phone run completed all 18 non-UI checks with success status, then stalled again at the UI fixture despite an unlocked phone. Full phone UI completion is not claimed; the separate final phone non-UI run reports **PASS, 18 tests**, 2.49 seconds. The full phone UI run exceeded the five-minute cap and was stopped; phone UI automation is **BLOCKED / NOT PASSED**. Emulator API 36: **PASS, 21 tests**, 108.416 seconds, including manual image review, attestation, confirmed history, navigation and preference recreation. The normal app was reopened on the phone with `Status: ok`, cold TotalTime 2149 ms. No visual screenshot verification was performed.

The first emulator run passed 20 of 21 checks; the existing history-navigation test asserted an empty state before asynchronous Room loading finished. It now waits explicitly for that state. No production record or error state was changed to satisfy the test.

Commands:

```powershell
Push-Location android
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug :app:assembleDebugAndroidTest --no-daemon --console=plain
Pop-Location
# Only after the phone owner authorizes app installation/testing:
.\tools\test-android-phone.ps1 -RunInstrumentation -NonUiOnly
python tools/verify_project_docs.py
```

The user explicitly authorized installing/updating and testing this app on their connected phone. Discovery found one authorized physical phone on API 34; no serial is recorded here. The phone helper selects only that single authorized physical transport, installs only this project app/test APKs, uses no device-wide animation setting changes, and opens only MainActivity. The helper streams test output to a private local log and stops only the project app/runner if a full run exceeds five minutes (two for the explicit non-UI subset). It does not clear existing real data. Existing navigation tests clear synthetic sample rounds and reset appearance; new import/review tests use temporary test databases/files. The final non-UI phone command is deliberately labeled as a subset and does not establish phone UI behavior. A Windows PowerShell process-handle issue initially produced a false helper failure after an explicit JUnit PASS; caching the handle before waiting fixed it, and the helper was rerun successfully.

Tests generate a small magenta PNG and five gray AVC video frames. These are explicit synthetic fixtures, not game screenshots or observations. No camera, screen or gallery capture is used to create them. Tests cover byte hashes, deduplication, media validation, limits/interruption cleanup, wrong provenance, mutation detection, correction history, stale writes, natural-key rollback/context reset, schema migration and an attested image-to-history UI flow.

## Limits

Real game-reference interpretation, automatic recognition, screen capture, prediction/model accuracy, PC synchronization and payout calculation are **NOT IMPLEMENTED / NOT TESTED**. No other app's private data, game screens or gallery enumeration are accessed. The user chooses references in the app. System picker interaction with the user's gallery and interpretation of actual reference material require the user's own selection; automated tests do not browse it.

Physical-phone UI automation remains incomplete despite two activity-harness attempts on an unlocked phone; no root cause is claimed. The emulator UI assertions pass. System document-picker/provider integration, video playback controls, real-reference interpretation, visual screenshots, TalkBack, exhaustive large-font/rotation coverage, other devices/codecs/Android versions and long-video performance remain **NOT TESTED**. Reference retention/export/backup is deferred to T019. Local manual reviews have a human-entered UTC observation time and a local reviewer pseudonym; this is an audit trail, not authenticated identity or a trusted clock. No full Android v1 schema conformance or immutable prediction persistence is claimed.

API guidance consulted: [Storage Access Framework](https://developer.android.com/training/data-storage/shared/documents-files), [MediaMetadataRetriever](https://developer.android.com/reference/android/media/MediaMetadataRetriever), [Room migrations](https://developer.android.com/training/data-storage/room/migrating-db-versions). Dependencies remain pinned to the verified T005 toolchain; no new library is introduced.
