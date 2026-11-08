$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$sdkRoot = if ($env:ANDROID_HOME) { $env:ANDROID_HOME } elseif ($env:ANDROID_SDK_ROOT) { $env:ANDROID_SDK_ROOT } else { Join-Path $env:LOCALAPPDATA 'Android\Sdk' }
$adbPath = Join-Path $sdkRoot 'platform-tools\adb.exe'
$avdName = & $adbPath -s emulator-5556 emu avd name
if ($LASTEXITCODE -ne 0 -or $avdName -notcontains 'GreedyCat_T005') { throw 'Only the isolated GreedyCat_T005 emulator on emulator-5556 is supported by this test helper.' }
$booted = & $adbPath -s emulator-5556 shell getprop sys.boot_completed
if ($LASTEXITCODE -ne 0 -or ($booted -join '').Trim() -ne '1') { throw 'Wait for the isolated emulator to finish booting first.' }
$appApk = Join-Path $projectRoot 'android\app\build\outputs\apk\debug\app-debug.apk'
$testApk = Join-Path $projectRoot 'android\app\build\outputs\apk\androidTest\debug\app-debug-androidTest.apk'
foreach ($apk in @($appApk, $testApk)) {
    if (-not (Test-Path -LiteralPath $apk -PathType Leaf)) { throw 'Build the app and instrumentation APKs before starting tests.' }
    & $adbPath -s emulator-5556 install -r $apk
    if ($LASTEXITCODE -ne 0) { throw 'Installing the project test APKs failed.' }
}
foreach ($setting in @('window_animation_scale', 'transition_animation_scale', 'animator_duration_scale')) {
    & $adbPath -s emulator-5556 shell settings put global $setting 0
    if ($LASTEXITCODE -ne 0) { throw 'Could not configure animation timing on the isolated emulator.' }
}
$output = & $adbPath -s emulator-5556 shell am instrument -w -r org.greedycat.intelligence.test/androidx.test.runner.AndroidJUnitRunner
$testExitCode = $LASTEXITCODE
$artifactRoot = Join-Path $projectRoot 'artifacts\android'
New-Item -ItemType Directory -Path $artifactRoot -Force | Out-Null
$output | Tee-Object -FilePath (Join-Path $artifactRoot 'instrumentation.txt')
if ($testExitCode -ne 0 -or -not ($output -match '^OK \([0-9]+ tests?\)\s*$')) { throw 'Android instrumentation did not report a complete PASS. Inspect the local artifact.' }
