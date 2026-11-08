param([switch]$PrepareOnly)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$sdkRoot = if ($env:ANDROID_HOME) { $env:ANDROID_HOME } elseif ($env:ANDROID_SDK_ROOT) { $env:ANDROID_SDK_ROOT } else { Join-Path $env:LOCALAPPDATA 'Android\Sdk' }
$emulatorPath = Join-Path $sdkRoot 'emulator\emulator.exe'
$managerPath = Join-Path $sdkRoot 'cmdline-tools\latest\bin\avdmanager.bat'
$adbPath = Join-Path $sdkRoot 'platform-tools\adb.exe'
foreach ($requiredPath in @($emulatorPath, $managerPath, $adbPath)) {
    if (-not (Test-Path -LiteralPath $requiredPath -PathType Leaf)) { throw 'Required Android SDK emulator/command-line/platform tools are missing.' }
}
$env:ANDROID_AVD_HOME = Join-Path $projectRoot 'artifacts\android\avd'
$env:ANDROID_USER_HOME = Join-Path $projectRoot 'artifacts\android\user'
New-Item -ItemType Directory -Path $env:ANDROID_AVD_HOME, $env:ANDROID_USER_HOME -Force | Out-Null
$avdPath = Join-Path $env:ANDROID_AVD_HOME 'GreedyCat_T005.avd'
if (-not (Test-Path -LiteralPath (Join-Path $avdPath 'config.ini'))) {
    'no' | & $managerPath create avd --name GreedyCat_T005 --package 'system-images;android-36;google_apis;x86_64' --path $avdPath --device pixel_2
    if ($LASTEXITCODE -ne 0) { throw 'AVD creation failed. Install the Android 36 Google APIs x86_64 system image first.' }
}
$devices = & $adbPath devices
if ($LASTEXITCODE -ne 0) { throw 'Local ADB discovery failed.' }
if ($devices -match '^emulator-5556\s+device\s*$') {
    $existingName = & $adbPath -s emulator-5556 emu avd name
    if ($existingName -notcontains 'GreedyCat_T005') { throw 'Emulator port 5556 belongs to another AVD. It was not changed.' }
    Write-Host 'Isolated GreedyCat_T005 emulator is already running.'
    return
}
if ($devices -match '^emulator-5556\s') { throw 'An unavailable emulator already occupies port 5556. It was not changed.' }
$configPath = Join-Path $avdPath 'config.ini'
$profile = Get-Content -LiteralPath $configPath
$profile = $profile -replace '^hw.lcd.width=.*$', 'hw.lcd.width=720' -replace '^hw.lcd.height=.*$', 'hw.lcd.height=1280' -replace '^hw.lcd.density=.*$', 'hw.lcd.density=320' -replace '^hw.ramSize=.*$', 'hw.ramSize=1024' -replace '^hw.cpu.ncore=.*$', 'hw.cpu.ncore=2'
Set-Content -LiteralPath $configPath -Value $profile -Encoding ASCII
if ($PrepareOnly) { Write-Host 'PASS: isolated emulator profile prepared'; return }
$logs = Join-Path $projectRoot 'artifacts\android'
$process = Start-Process -FilePath $emulatorPath -ArgumentList @('-avd', 'GreedyCat_T005', '-port', '5556', '-memory', '1024', '-cores', '2', '-no-window', '-no-audio', '-no-boot-anim', '-no-snapshot', '-gpu', 'swiftshader_indirect', '-camera-back', 'none', '-camera-front', 'none') -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $logs 'emulator.stdout.log') -RedirectStandardError (Join-Path $logs 'emulator.stderr.log')
Write-Host "Started isolated headless emulator; process $($process.Id), ADB emulator-5556."
Write-Host 'Wait for sys.boot_completed=1 before running tests. Stop only this AVD with: adb -s emulator-5556 emu kill'
