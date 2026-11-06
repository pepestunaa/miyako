$ErrorActionPreference = "Stop"

$sdkRoot = "C:/Users/surya/AppData/Local/Android/Sdk"
$adb = Join-Path $sdkRoot "platform-tools/adb.exe"
$device = "emulator-5554"

$devices = & $adb devices
if (($devices | Select-String "^$device\s+device$") -eq $null) {
    Write-Host "Emulator tidak sedang berjalan."
    exit 0
}

& $adb -s $device emu kill
if ($LASTEXITCODE -ne 0) {
    throw "Emulator gagal dihentikan."
}

Write-Host "Emulator dihentikan."
