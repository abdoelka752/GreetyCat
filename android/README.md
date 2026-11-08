# Android research companion (T005–T006)

Kotlin/Compose companion application with offline Room storage, overview/history/settings navigation, reference import, manual result review and an explicit demo workspace. It does not capture a screen, recognize results automatically, generate predictions or synchronize with the PC yet.

## Toolchain

| Component | Pinned choice |
|---|---|
| Java | JDK 17 |
| Gradle / Android plugin | 8.13 / 8.13.2 |
| Kotlin / Compose compiler | 2.2.21 |
| KSP | 2.2.21-2.0.4 |
| Compose BOM | 2025.11.01 |
| Room | 2.8.5 |
| Activity / Lifecycle / Navigation | 1.11.0 / 2.9.4 / 2.9.5 |
| Coroutines | 1.10.2 |
| Serialization runtime | 1.8.1 |
| Compile / target / minimum SDK | 36 / 36 / 26 |
| Build tools | 36.0.0 |
| Application ID | org.greedycat.intelligence |

This is a deliberately compatible pinned toolchain for the installed JDK/SDK/Gradle. It is not a claim that every dependency is the newest release. Upgrade/target-SDK lint advisories remain visible and are documented in the task report. Android 8.0+ is the intended minimum; only the reported test environment is verified.

The wrapper distribution has a pinned official SHA-256, and its JAR was verified against Gradle's official checksum. Gradle uses a 1 GiB heap, one worker, a single-use process and in-process Kotlin compilation to keep this 8 GB Windows machine's workload bounded. Do not run a full build and emulator simultaneously on this host.

## Build on Windows

Install Android SDK platform 36, build tools 36.0.0 and platform tools. Set JAVA_HOME to JDK 17 and ANDROID_HOME to your SDK (or let Android Studio generate an ignored local.properties file).

From repository root:

```powershell
$env:ANDROID_HOME = Join-Path $env:LOCALAPPDATA 'Android\Sdk'
Push-Location android
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug :app:assembleDebugAndroidTest --no-daemon --console=plain
Pop-Location
```

The first run downloads pinned Maven dependencies. Artifacts are local/ignored:

- `app/build/outputs/apk/debug/app-debug.apk`
- `app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk`
- `app/build/test-results/testDebugUnitTest/`
- `app/build/reports/lint-results-debug.html`

Debug signing uses the local Android debug key, which is never committed. Release signing/distribution is not part of T005. If supplying local.properties manually, escape the Windows drive colon (`sdk.dir=C\:/...`) and backslashes, or use ANDROID_HOME instead.

## Isolated emulator verification

Install `system-images;android-36;google_apis;x86_64`, Android Emulator and command-line tools. Hardware acceleration must be available. The helper creates an owned, headless `GreedyCat_T005` AVD under ignored `artifacts/android/`, requesting 1 GiB guest RAM, two guest cores and software graphics. Emulator 37.2.12 increased that request to its 2.5 GiB minimum in this run; inspect startup logs rather than assuming the request is a hard cap. It uses emulator port 5556 and refuses to change another AVD already using that serial.

After the build's Java process has exited:

```powershell
.\tools\android-emulator.ps1
adb -s emulator-5556 shell getprop sys.boot_completed
# Wait until the command reports 1, then:
.\tools\test-android-emulator.ps1
adb -s emulator-5556 shell am start -W -n org.greedycat.intelligence/.MainActivity
```

The test helper installs only this project's app/test APKs on the named owned emulator, disables its animations, and runs AndroidJUnitRunner directly via ADB. This avoids keeping Gradle's VM in memory alongside the emulator. It requires an explicit `OK (N tests)` result; ADB exit code alone is insufficient. Logs are saved locally at `artifacts/android/instrumentation.txt`. It never selects a physical phone automatically, installs to unrelated devices, controls the game, or invokes root.

Stop the owned emulator after verification:

```powershell
adb -s emulator-5556 emu kill
```

To install on a physical phone later, explicitly choose and authorize that device before installing. ADB availability does not grant screen capture or navigation permission. This app declares no network, capture, shared-storage or background-service permissions.

## Data boundaries

The application container owns two distinct Room files: `greedycat-research.db` for REAL records and `greedycat-demo.db` for SYNTHETIC records. Real history is empty on first launch. Settings -> Open demo workspace -> Add sample draft stores one fixed, explicitly synthetic, unconfirmed draft corresponding to the shared round-open fixture; repeated inserts are idempotent. Demo entry is explicit, does not become the default workspace and does not appear in real history.

Room stores session identity, draft rounds and per-field missing-data flags in separate relational tables. Composite foreign keys enforce session/context/mode/provenance agreement; a unique context/mode/round-number index prevents duplicate numbered rounds while nullable unknown numbers remain unknown. Draft validation rejects invalid UUIDs/timestamps, inconsistent identities, mixed provenance and attempts to promote a draft directly to a confirmed/completed phase. Inserts compare existing content within a transaction, never replace it. Conflicts roll back the entire operation. T006 confirmation uses a separate append-only review chain.

History and reference lists return at most 100 recent records per workspace. A pending draft is not a training outcome; confirmed history derives its latest value from human reviews. Prediction, Wager, wire export and synchronization entities remain future work. Complete Android contract ingestion/transport remains later work.

Room schema v1 was generated during T005 before adding the persisted compact-spacing preference. Schema v2 adds `workspace_preferences`; the explicit 1->2 migration preserves prior draft records and missing-field rows. T006 schema v3 adds references, local identity/context and review revisions through a 2->3 migration. All three generated schemas are tracked under `app/schemas/`. V1 is a development schema, not a previously released app. There is no destructive-migration fallback. Compact spacing is persisted through Room in the real workspace, and settings/database failures have visible messages.

The app explicitly pins serialization JSON/core to 1.8.1, matching Room's migration schema reader. Navigation otherwise resolves 1.7.3 in the app while the test APK resolves 1.8.1, causing an instrumentation classloader ABI error. Keep both runtimes aligned when upgrading.

Automatic cloud/device-transfer backup is excluded; deliberate backup/export belongs to T019. No user captures, databases, keys, SDK paths, emulator state or test artifacts enter Git.

## Import and review (T006)

Open History -> Import image or video. Select only a file you are authorized to use. Android's document picker grants access to that file; no gallery enumeration or shared-storage permission is requested. Supported media: PNG, JPEG, WebP, MP4 and WebM, at most 128 MiB, images up to 40 megapixels and videos up to two hours. The original bytes are copied privately, validated and SHA-256 hashed. Source names/paths/URIs are not persisted. Reimporting identical bytes reuses the reference.

Choose Review reference. Inspect the image or use the video's playback controls and pause-position button. Enter the observed game mode, canonical round number, outcome, UTC observation timestamp (`YYYY-MM-DDTHH:MM:SS.sssZ`) and evidence description. Video results also require a position within the recording. Import time is not observation time. Check the attestation before confirming. Human confirmation is not automated recognition or proof of predictive skill.

Confirmed history shows the latest human review. Review or correct opens its audit trail. Corrections preserve prior values and require a reason; a stale predecessor cannot overwrite a newer review. Mode, round number and context cannot change as part of a correction. Each workspace retains a context for round uniqueness; use Start a new context only for a distinct/reset history. The reviewer is a local pseudonym, not authenticated identity. Clock trust is not established.

For sample references, use Settings -> Open demo workspace -> Review demo references. Those files and reviews stay SYNTHETIC and separate from real history. Clear demo records removes demo rounds/reviews; reference originals remain retained. Retention/deletion/export controls belong to T019.

## Authorized physical-phone check

With exactly one authorized physical phone connected, and only after its owner approves app installation/testing:

```powershell
.\tools\test-android-phone.ps1 -RunInstrumentation
# For the explicit non-UI storage/media/migration subset:
.\tools\test-android-phone.ps1 -RunInstrumentation -NonUiOnly
```

The helper installs this app and its tests, keeps real data, uses temporary synthetic fixtures/databases for new review tests, and opens only MainActivity. Existing navigation tests clear demo sample rounds and reset compact spacing. No screen capture, gallery browsing, game control or device-wide settings changes occur. Results stay in ignored `artifacts/android/t006-phone-instrumentation.txt` or `t006-phone-storage.txt`; identifiers are not logged. Runs are bounded to five minutes (two for the non-UI subset), and only this project's runner/app is stopped on timeout. A successful subset is not a full UI pass. T006 verified 18 non-UI checks on the API 34 phone and all 21 checks on the API 36 emulator; the full phone UI run stalled and remains unverified.

See the [T006 report](../docs/reports/T006_REFERENCE_REVIEW.md), [task board](../docs/TASKS.md) and [architecture](../docs/ARCHITECTURE.md).
