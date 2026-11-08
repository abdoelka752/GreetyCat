# T007 statistical research and immutable checkpoints — 2026-11-08

## Implemented scope and acceptance

Offline research summaries of eligible human-reviewed history, a symmetric Dirichlet baseline with explicit assumptions, analytical parameter uncertainty, bounded provenance verification, and an immutable local ledger for prospective manual abstention checkpoints. Test normalization/posterior arithmetic, empty/sparse history, as-of corrections, mode/context isolation, target-result leakage, immutable persistence, migration and UI behavior before publishing the task checkpoint.

**Manual checkpoints always ABSTAIN.** The application has no verified live acquisition or trusted game deadline yet. The user must attest that a manually entered current round's result is unknown; this is unverified input, not proof of live observation. A manual window of 5–120 seconds is a local deadline, not an inferred 20-second game countdown. No live probability forecast, wagering recommendation, payout calculation or demonstrated predictive edge is issued. These limits are visible in the UI.

Overview -> Open research selects BASIC/PROFESSIONAL in the active workspace context. Compute historical summary explicitly verifies evidence and displays reviewed counts/frequencies, posterior means, parameter SD and normalized predictive entropy. Empty history displays no probability table. The demo route additionally offers a fixed-count mathematical example, labeled SYNTHETIC, which writes no observations or predictions. Real history is never seeded.

Record abstention checkpoint creates an unverified UNKNOWN-phase draft with unknown game start time and a locked ANDROID record in one transaction. Existing/imported targets, reused round identities, changed request identifiers, clock changes and expired windows are rejected. History can attach a selected reference to a pending round for subsequent human review. A later result or correction does not rewrite the checkpoint. Demo round clearing retains immutable ledger snapshots.

## Statistical model and uncertainty

For each of the ten contract outcome categories, prior alpha is 0.5. Given reviewed counts n_i and N=sum(n_i), posterior alpha a_i=n_i+0.5 and A=N+5. Empirical frequency is n_i/N for N>0; posterior mean is a_i/A; parameter variance is a_i(A-a_i)/(A²(A+1)). Parameter SD is the square root of that variance. Predictive entropy is -sum(p_i log p_i)/log(10).

This assumes a stationary independent categorical process and a symmetric prior; neither is a verified Greedy Cat mechanic. Parameter SD is conditional uncertainty about the model parameters, not measured predictive calibration or a guaranteed interval. High outcome entropy can remain when parameter SD shrinks. Minimum 30 observations is an explicit research-policy guard, not a validated adequacy or skill threshold. Manual acquisition still forces abstention above that threshold. Skill remains NO_PROVEN_EDGE.

The pure policy supports testing a future verified-target eligibility gate; no production API marks a manual target verified. No heavy statistics, AI, sampling or acceleration dependency was added. Current wire v1 uncertainty remains NOT_ESTIMATED; local analytical SD is stored in a separately versioned local research snapshot. Full v1 JSON serialization/export/transport is not claimed or silently changed.

## Chronology, evidence and persistence

Room v4 adds `locked_predictions` and unique indexes for (roundId, source) and (contextId, mode, roundNumber, source). Source is ANDROID in the shipped repository. SQLite triggers prohibit updates/deletion of locked records. Triggers are installed both on fresh database creation and explicit 3->4 migration. Ledger rows contain issued/features/training cutoff/expiry times, context, mode, provenance, model and dataset IDs, status/reason, explicit MANUAL_UNVERIFIED acquisition and unknown-result attestation, an immutable snapshot JSON and its SHA-256. No destructive migration is used. A deliberate future retention workflow is required for deleting ledger records.

Eligible history uses the highest review revision confirmed strictly before the features cutoff, not today's latest revision applied retrospectively. Observed time must also precede that cutoff; import/observation/confirmation order is checked. Context, mode and provenance are isolated. A result known before recording prevents a new target; results learned later enter only subsequent datasets. Replay of the same checkpoint identity returns its original record, including after expiry; it never issues a fresh prediction.

Limits are 1,000 reviewed rounds and 256 MiB of unique evidence to rehash per operation. Exceeding a bound or failing evidence integrity produces no numeric summary and a checkpoint must abstain; history is not silently truncated. UI/history reads show the latest 100 ledger rows. Snapshot copies retain the selected review IDs, values, evidence hashes, times, video positions and reviewer pseudonyms even after later source corrections or demo round clearing. Archived natural round identities cannot be reused in the same context after demo drafts are cleared.

UTC issue times come from the device at recording, never from caller-supplied historical timestamps. Elapsed realtime is compared with UTC elapsed duration during calculation/transaction; divergence over one second, backwards time or expiry rejects the attempt. The latest stored issue time also blocks clock rollback between checkpoints. These guards are local consistency checks, not authenticated time, external outcome-knowledge attestation or a cross-device clock protocol. Trusted clocks and capture remain later work.

## Verification

Build/debug and test APKs: **PASS** (full final source build 6m 26s; final test-fixture rebuild 3m 35s). JVM: **PASS, 20 tests**. Lint: **PASS, 0 errors / 13 upgrade advisories** (5 dependency updates, 5 newer-version notices, 2 AGP notices, 1 target-SDK advisory). No new test-fixture compiler warnings remain.

Physical phone API 34: **PASS, 29 non-UI tests**, 7.462 seconds, explicit `OK (29 tests)`. This covers repository/chronology/immutability, migration and existing media/contract checks; it is not phone UI verification. The app was updated and reopened successfully: MainActivity `Status: ok`, cold TotalTime 1944 ms (one launch, not a performance benchmark). Owned API 36 emulator: **PASS, 33 tests**, 71.485 seconds, explicit `OK (33 tests)`, including the attested abstention UI, reference review, navigation and appearance recreation. No visual screenshot review was performed. Documentation/provenance checks and staged whitespace checks PASS.

The first phone run exposed a JUnit fixture whose expression-bodied restart test inferred a non-void result. An explicit Unit return fixed test discovery; the full class then passed. Two mixed-type SQL fixture arrays were made explicit to remove compiler warnings.

APK permissions: **PASS**, only the app-scoped signature receiver permission; no network, capture or shared-storage permissions.

APK: 12,299,468 bytes; SHA-256 `953dc3ff4b1e302bfc3d1e737edca62ca0f727fbc7868e6123de0e4a5352574f`.

```powershell
Push-Location android
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug :app:assembleDebugAndroidTest --no-daemon --console=plain
Pop-Location
# After Gradle exits, use the owned emulator helper:
.\tools\android-emulator.ps1
# Wait for emulator-5556 sys.boot_completed=1
.\tools\test-android-emulator.ps1
# Phone app installation/testing was authorized by the owner in this session:
.\tools\test-android-phone.ps1 -RunInstrumentation -NonUiOnly
python tools/verify_project_docs.py
```

App ID remains org.greedycat.intelligence, version 0.7.0 (code 3). JDK/Gradle/Android dependencies remain pinned to T005's compatible toolchain. Existing T006 tests use explicit synthetic media; prediction tests use isolated databases, generated synthetic evidence and injected synthetic clocks. No real gameplay observations or predictions are fabricated by tests.

## Limits and references

Actual predictive skill/calibration, real game-reference interpretation, live acquisition, screen/game visibility, capture, authenticated clocks, Android-PC synchronization and payout calculations: **NOT IMPLEMENTED / NOT TESTED**. Full physical-phone UI automation previously stalled in T006 and is not represented by the phone non-UI subset. Visual screenshots, real file-picker/provider interaction, video playback controls, other devices/OS versions, battery/thermal performance and long-term operation remain unverified. Model evaluation belongs to T014; retention/export to T019.

Primary guidance consulted: [Stan Dirichlet reference](https://mc-stan.org/docs/functions-reference/simplex_distributions.html), [Android SystemClock](https://developer.android.com/reference/android/os/SystemClock), [Room database callbacks](https://developer.android.com/reference/androidx/room/RoomDatabase.Callback). The posterior/moment formulas above are analytical calculations tested against explicit hand-computed cases, not empirical game claims.
