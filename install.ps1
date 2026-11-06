$ErrorActionPreference = "Stop"

$projectRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$sdkRoot = "C:/Users/surya/AppData/Local/Android/Sdk"
$adb = Join-Path $sdkRoot "platform-tools/adb.exe"
$device = "emulator-5554"
$apk = Join-Path $projectRoot "app/build/outputs/apk/standard/debug/app-standard-x86_64-debug.apk"

$devices = & $adb devices
if (($devices | Select-String "^$device\s+device$") -eq $null) {
    throw "Emulator belum berjalan. Jalankan .\start.ps1 terlebih dahulu."
}

Write-Host "Memastikan emulator sudah selesai boot..."
$deadline = (Get-Date).AddMinutes(3)
do {
    $bootCompleted = (& $adb -s $device shell getprop sys.boot_completed 2>$null).Trim()
    if ($bootCompleted -eq "1") {
        break
    }
    Start-Sleep -Seconds 3
} while ((Get-Date) -lt $deadline)

if ($bootCompleted -ne "1") {
    throw "Emulator belum selesai boot dalam 3 menit."
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
