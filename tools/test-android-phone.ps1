param([switch]$RunInstrumentation, [switch]$NonUiOnly)
$ErrorActionPreference = 'Stop'
if ($NonUiOnly -and -not $RunInstrumentation) { throw 'NonUiOnly requires RunInstrumentation.' }
# Run only after the owner authorizes installing/testing this project on their phone.
# Device discovery alone is not authorization. No gallery/game navigation or capture.
$projectRoot = Split-Path -Parent $PSScriptRoot
$sdkRoot = if ($env:ANDROID_HOME) { $env:ANDROID_HOME } elseif ($env:ANDROID_SDK_ROOT) { $env:ANDROID_SDK_ROOT } else { Join-Path $env:LOCALAPPDATA 'Android\Sdk' }
$adbPath = Join-Path $sdkRoot 'platform-tools\adb.exe'
$transports = & $adbPath devices
if ($LASTEXITCODE -ne 0) { throw 'ADB discovery failed.' }
$phones = @($transports | ForEach-Object { if ($_ -match '^([^\s]+)\s+device\s*$' -and $Matches[1] -notlike 'emulator-*') { $Matches[1] } })
if ($phones.Count -ne 1) { throw 'Exactly one authorized physical phone must be connected. No device was selected.' }
$serial = $phones[0]
$api = & $adbPath -s $serial shell getprop ro.build.version.sdk
if ($LASTEXITCODE -ne 0 -or [int]($api -join '') -lt 26) { throw 'Android API 26 or newer is required.' }
Write-Host "Selected the single authorized physical phone (API $api); its identifier is not logged."
$appApk = Join-Path $projectRoot 'android\app\build\outputs\apk\debug\app-debug.apk'
if (-not (Test-Path -LiteralPath $appApk -PathType Leaf)) { throw 'Build the debug APK first.' }
& $adbPath -s $serial install -r $appApk
if ($LASTEXITCODE -ne 0) { throw 'App installation failed; existing app data was not cleared.' }
if ($RunInstrumentation) {
    $testApk = Join-Path $projectRoot 'android\app\build\outputs\apk\androidTest\debug\app-debug-androidTest.apk'
    if (-not (Test-Path -LiteralPath $testApk -PathType Leaf)) { throw 'Build the instrumentation APK first.' }
    & $adbPath -s $serial install -r $testApk
    if ($LASTEXITCODE -ne 0) { throw 'Test APK installation failed.' }
    $artifactRoot = Join-Path $projectRoot 'artifacts\android'
    New-Item -ItemType Directory -Path $artifactRoot -Force | Out-Null
    $logBase = if ($NonUiOnly) { 't006-phone-storage' } else { 't006-phone-instrumentation' }
    $testLog = Join-Path $artifactRoot "$logBase.txt"
    $errorLog = Join-Path $artifactRoot "$logBase.stderr.txt"
    $runnerArguments = @('-s', $serial, 'shell', 'am', 'instrument', '-w', '-r')
    if ($NonUiOnly) {
        $testClasses = @('data.ReferenceReviewTest', 'data.ResearchMigrationTest', 'data.ResearchRepositoryTest', 'domain.SharedDraftFixtureTest') | ForEach-Object { "org.greedycat.intelligence.$_" }
        $runnerArguments += @('-e', 'class', ($testClasses -join ','))
        Write-Host 'Running the explicit 18-test non-UI subset; this does not verify phone UI.'
    }
    $runnerArguments += 'org.greedycat.intelligence.test/androidx.test.runner.AndroidJUnitRunner'
    $runner = Start-Process -FilePath $adbPath -ArgumentList $runnerArguments -WindowStyle Hidden -PassThru -RedirectStandardOutput $testLog -RedirectStandardError $errorLog
    # Cache the native handle before exit; Windows PowerShell otherwise can
    # return a null ExitCode after waiting on a short-lived child process.
    $runnerHandle = $runner.Handle
    $elapsed = [Diagnostics.Stopwatch]::StartNew()
    $limitSeconds = if ($NonUiOnly) { 120 } else { 300 }
    while (-not $runner.WaitForExit(1000)) {
        if ($elapsed.Elapsed.TotalSeconds -gt $limitSeconds) {
            Stop-Process -Id $runner.Id -ErrorAction SilentlyContinue
            & $adbPath -s $serial shell am force-stop org.greedycat.intelligence
            throw "Phone test run exceeded $limitSeconds seconds; only the project runner/app were stopped. Inspect local logs."
        }
    }
    $runner.WaitForExit()
    $runner.Refresh()
    $instrumentExit = $runner.ExitCode
    $output = Get-Content -LiteralPath $testLog
    $summary = $output | Where-Object { $_ -match '^(Time:|OK \(|FAILURES|Tests run:|INSTRUMENTATION_FAILED)' }
    $summary | Write-Host
    if ($instrumentExit -ne 0 -or -not ($output -match '^OK \([0-9]+ tests?\)\s*$')) { throw 'Phone instrumentation did not report PASS. Inspect the ignored local test log.' }
}
& $adbPath -s $serial shell am force-stop org.greedycat.intelligence
$launch = & $adbPath -s $serial shell am start -W -n org.greedycat.intelligence/.MainActivity
$launchExit = $LASTEXITCODE
$launch | Write-Host
if ($launchExit -ne 0 -or -not ($launch -match '^Status: ok\s*$')) { throw 'App launch did not report Status: ok.' }
Write-Host 'Phone testing finished. No screen capture, gallery enumeration or game control was performed.'
