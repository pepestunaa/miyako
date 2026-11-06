$ErrorActionPreference = "Stop"

$sdkRoot = "C:/Users/surya/AppData/Local/Android/Sdk"
$adb = Join-Path $sdkRoot "platform-tools/adb.exe"
$device = "emulator-5554"
$packageName = "eu.kanade.tachiyomi.debugYokai"
$launcher = "$packageName/.MainActivityDefaultDummy"

$devices = & $adb devices
if (($devices | Select-String "^$device\s+device$") -eq $null) {
    throw "Emulator belum berjalan. Jalankan .\start-emulator.ps1 terlebih dahulu."
}

Write-Host "Membuka Miyako..."
& $adb -s $device shell am force-stop $packageName
& $adb -s $device shell am start -n $launcher
if ($LASTEXITCODE -ne 0) {
    throw "Aplikasi gagal dibuka."
}

Write-Host "Miyako berhasil dijalankan."
