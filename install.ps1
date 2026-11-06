$ErrorActionPreference = "Stop"

$projectRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$sdkRoot = "C:/Users/surya/AppData/Local/Android/Sdk"
$adb = Join-Path $sdkRoot "platform-tools/adb.exe"
$device = "emulator-5554"
$apk = Join-Path $projectRoot "app/build/outputs/apk/standard/debug/app-standard-x86_64-debug.apk"

$devices = & $adb devices
if (($devices | Select-String "^$device\s+device$") -eq $null) {
    throw "Emulator belum berjalan. Jalankan .\start-emulator.ps1 terlebih dahulu."
}

if (-not (Test-Path $apk)) {
    throw "APK belum tersedia. Jalankan .\build.ps1 terlebih dahulu."
}

Write-Host "Meng-install APK ke emulator..."
& $adb -s $device install -r $apk
if ($LASTEXITCODE -ne 0) {
    throw "Install APK gagal."
}

Write-Host "Install berhasil."
