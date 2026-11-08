# Android foundation (T005)

Kotlin/Compose companion application with offline Room storage, overview/history/settings navigation and an explicit demo workspace. This foundation does not import references, capture a screen, confirm outcomes, generate predictions or synchronize with the PC yet.

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

Room stores capture-session identity, draft rounds and per-field missing-data flags in separate relational tables. Composite foreign keys enforce session/context/mode/provenance agreement; a unique context/mode/round-number index prevents duplicate numbered rounds while nullable unknown numbers remain unknown. Repository validation rejects invalid UUIDs/timestamps, inconsistent identities, mixed provenance and confirmed/completed states without an outcome implementation. Inserts compare existing content within a transaction, never replace it. Conflicts roll back the entire operation.

Queries return at most 100 recent drafts per workspace. These are drafts, not training outcomes. Final Outcome, Prediction, Correction, Wager and synchronization entities are deferred to their respective tasks. T005 is not full Android conformance with all v1 wire contracts: one shared draft fixture is checked, while complete contract ingestion/transport remains later work.

Room schema v1 was generated during this task before adding the persisted compact-spacing preference. Schema v2 adds `workspace_preferences`; the explicit 1->2 migration preserves prior draft records and missing-field rows. Both generated schemas are tracked under `app/schemas/`. V1 is a development schema, not a previously released app. There is no destructive-migration fallback. Compact spacing is persisted through Room in the real workspace, and settings/database failures have visible messages.

The app explicitly pins serialization JSON/core to 1.8.1, matching Room's migration schema reader. Navigation otherwise resolves 1.7.3 in the app while the test APK resolves 1.8.1, causing an instrumentation classloader ABI error. Keep both runtimes aligned when upgrading.

Automatic cloud/device-transfer backup is excluded; deliberate backup/export belongs to T019. No user captures, databases, keys, SDK paths, emulator state or test artifacts enter Git.

See the [task board](../docs/TASKS.md) and [architecture](../docs/ARCHITECTURE.md). T006 adds reference import/manual review; this foundation exposes no pretend import/capture buttons.
