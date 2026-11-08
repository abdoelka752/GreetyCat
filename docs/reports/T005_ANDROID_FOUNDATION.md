# T005 Android foundation — 2026-11-08

## Implemented scope

One offline Android app module, application ID `org.greedycat.intelligence`, with Kotlin/Compose overview, history, settings and a separate explicit demo route. The dark amber theme, local icon and navigation are implemented. Real history starts empty. No game data, confirmed outcomes or predictions are generated.

Room stores sessions, draft rounds, missing-field metadata and compact-spacing preferences. Real and synthetic workspaces use separate files and provenance-bound repositories. Transactional compare-and-insert prevents replay duplicates and conflicting replacement; a natural-key conflict rolls back a newly inserted session. Composite foreign keys reject mismatched session metadata. Unknown round numbers remain nullable. Queries are bounded to 100 recent drafts. Clear-demo cascades only demo records.

Generated schema v1 was created during development; v2 adds display preferences through an explicit migration. Both schemas are tracked. No destructive-migration fallback is enabled. V1 was not a previously published app. Outcome, correction, prediction and outbox entities belong to later tasks.

## Build and verification commands

See [Android README](../../android/README.md) for setup and commands. Final build/check run:

```powershell
Push-Location android
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug :app:assembleDebugAndroidTest --no-daemon --console=plain
Pop-Location
.\tools\android-emulator.ps1
# Wait for emulator-5556 sys.boot_completed=1
.\tools\test-android-emulator.ps1
```

The instrumentation helper invokes AndroidJUnitRunner directly after Gradle exits, reducing simultaneous memory use. It verifies the owned AVD name, installs only the project APKs, disables animations there, and requires an explicit JUnit `OK` result. Emulator state, build logs, test output and screenshots stay under ignored local paths.

Build, debug APK, test APK and lint: **PASS**, final build 8m 18s. JVM tests: **PASS, 8**, zero failures/errors/skips. Lint: **PASS, 0 errors, 13 warnings** (5 GradleDependency, 5 NewerVersionAvailable, 2 AndroidGradlePluginVersion, 1 OldTargetApi). Upgrade advisories remain visible. Emulator rerun: **PASS, 12 tests**, 65.583 seconds, explicit `OK (12 tests)`. The migration test now passes with the aligned runtime. App-only force-stop/cold-launch smoke: **PASS**, `am start -W` returned `Status: ok`, expected MainActivity, process running; reported TotalTime 9313 ms. This cold emulator launch is not a phone latency benchmark.

APK permission inspection: **PASS**; only the app-scoped signature permission `DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` is requested. No network, capture or shared-storage permissions. Debug APK: 11,994,763 bytes; SHA-256 `7bf2afdd2be5894932458ab19286ab693ebd712152090ec85de8dea02a86a566`. The unstripped debug `libandroidx.graphics.path.so` packaging advisory is nonfatal.

Meaningful coverage includes canonical IDs/timestamps, identity/provenance, unknown fields, phase/time guards, replay/conflict rollback, cross-mode/context uniqueness, foreign keys, cascade, separate-file close/reopen persistence, v1->v2 migration, shared synthetic draft identity, Compose navigation/demo isolation and persisted appearance across activity recreation.

## Compatibility and fixes

Pinned JDK 17 / Gradle 8.13 / AGP 8.13.2 / Kotlin 2.2.21 / KSP 2.2.21-2.0.4 / Compose BOM 2025.11.01 / Room 2.8.5 / SDK 36, minimum 26. The selection favors compatibility with the installed environment; it is not a latest-version claim. Direct pins and wrapper distribution SHA-256 are tracked. The official wrapper JAR SHA-256 was checked: `81a82aaea5abcc8ff68b3dfcb58b3c3c429378efd98e7433460610fecd7ae45f`.

The initial lint run found an API 27 style attribute against minimum API 26 and an incorrectly escaped local Windows SDK path. Both were fixed. Automatic backup has explicit exclusions for cloud/device transfer and `fullBackupContent=false` for older versions.

The first instrumentation run passed 11 of 12 tests; the migration helper failed with `GeneratedSerializer.typeParametersSerializers` AbstractMethodError before reading its schema. Dependency inspection found serialization core 1.7.3 in the main APK and Room migration's 1.8.1 runtime in the test APK. The app now explicitly aligns serialization JSON/core to 1.8.1. The migration test was retained and rerun.

Official references consulted for compatibility and test APIs:

- [AGP 8.13 compatibility](https://developer.android.com/build/releases/agp-8-13-0-release-notes)
- [Room releases](https://developer.android.com/jetpack/androidx/releases/room)
- [Room database testing](https://developer.android.com/training/data-storage/room/testing-db)
- [KSP 2.2.21-2.0.4](https://github.com/google/ksp/releases/tag/2.2.21-2.0.4)
- [Serialization 1.8.1 release](https://github.com/Kotlin/kotlinx.serialization/releases/tag/v1.8.1)
- [Gradle wrapper checksums](https://gradle.org/release-checksums/)

## Environment and limits

Windows 10, i5-7200U, 8 GB RAM, Intel HD 620; headless owned `GreedyCat_T005` Android 36 Google APIs x86_64 AVD, emulator 37.2.12 with WHPX, two guest cores, 720x1280 at density 320, software graphics. The emulator increased the requested 1024 MiB to its 2560 MiB minimum; observed QEMU working set was about 2.2 GB. This is a resource observation, not a performance benchmark. Gradle uses one worker and a 1 GiB heap; build and emulator were run sequentially.

Physical-device validation, Android API 26 behavior, other OS versions, TalkBack, large fonts, rotation, battery/latency profiling and release distribution: **NOT TESTED**. No phone/game navigation, screen capture, protected-data access or wagering occurred. Visual screenshot review: **NOT TESTED**. Automatic approval review rejected the proposed emulator screenshot because capture requires explicit consent; no screenshot was taken and no alternative capture was attempted. Compose assertions validate the implemented navigation and demo flows, but do not establish visual appearance.

Complete Android v1 JSON-schema conformance is **NOT IMPLEMENTED**. One shared draft fixture is checked; this does not prove outcome/prediction/event ingestion or Android-PC transport. T006 adds offline reference import/manual review. Capture is T012; pairing is T008; first hybrid gameplay integration remains T010.
