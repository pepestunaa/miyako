$ErrorActionPreference = "Stop"

$sdkRoot = "C:/Users/surya/AppData/Local/Android/Sdk"
$adb = Join-Path $sdkRoot "platform-tools/adb.exe"
$emulator = Join-Path $sdkRoot "emulator/emulator.exe"
$device = "emulator-5554"
$avd = "miyako-dev"

$devices = & $adb devices
if (($devices | Select-String "^$device\s+device$") -ne $null) {
    Write-Host "Emulator sudah berjalan."
    exit 0
}

Write-Host "Menyalakan emulator $avd..."
Start-Process -FilePath $emulator -ArgumentList "-avd", $avd, "-gpu", "host"
& $adb -s $device wait-for-device

Write-Host "Menunggu emulator selesai menyala..."
$deadline = (Get-Date).AddMinutes(3)
do {
    $bootCompleted = (& $adb -s $device shell getprop sys.boot_completed 2>$null).Trim()
    if ($bootCompleted -eq "1") {
        Write-Host "Emulator siap."
        exit 0
    }
    Start-Sleep -Seconds 3
} while ((Get-Date) -lt $deadline)

throw "Emulator tidak selesai boot dalam 3 menit."
