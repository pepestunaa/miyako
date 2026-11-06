param(
    [switch]$NoBuild
)

$ErrorActionPreference = "Stop"

$projectRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$sdkRoot = "C:/Users/surya/AppData/Local/Android/Sdk"
$adb = Join-Path $sdkRoot "platform-tools/adb.exe"
$emulator = Join-Path $sdkRoot "emulator/emulator.exe"
$device = "emulator-5554"
$avd = "miyako-dev"
$packageName = "eu.kanade.tachiyomi.debugYokai"
$launcher = "$packageName/.MainActivityDefaultDummy"
$apk = Join-Path $projectRoot "app/build/outputs/apk/standard/debug/app-standard-x86_64-debug.apk"

function Test-EmulatorConnected {
    $devices = & $adb devices
    return ($devices | Select-String "^$device\s+device$") -ne $null
}

function Wait-ForBoot {
    Write-Host "Menunggu emulator selesai menyala..."
    & $adb -s $device wait-for-device

    $deadline = (Get-Date).AddMinutes(3)
    do {
        $bootCompleted = (& $adb -s $device shell getprop sys.boot_completed 2>$null).Trim()
        if ($bootCompleted -eq "1") {
            Write-Host "Emulator siap."
            return
        }
        Start-Sleep -Seconds 3
    } while ((Get-Date) -lt $deadline)

    throw "Emulator tidak selesai boot dalam 3 menit."
}

if (-not (Test-EmulatorConnected)) {
    Write-Host "Menyalakan emulator $avd..."
    Start-Process -FilePath $emulator -ArgumentList "-avd", $avd, "-gpu", "host"
    Wait-ForBoot
} else {
    Write-Host "Emulator sudah berjalan."
}

if (-not $NoBuild) {
    Write-Host "Build dan install aplikasi..."
    Push-Location $projectRoot
    try {
        & ".\gradlew.bat" ":app:installStandardDebug" "-PdevAbi=x86_64" "--parallel" "--console=plain" "-x" "lint"
        if ($LASTEXITCODE -ne 0) {
            throw "Build atau install gagal."
        }
    } finally {
        Pop-Location
    }
} else {
    if (-not (Test-Path $apk)) {
        throw "APK belum tersedia. Jalankan tanpa -NoBuild terlebih dahulu."
    }

    Write-Host "Install APK terakhir..."
    & $adb -s $device install -r $apk
    if ($LASTEXITCODE -ne 0) {
        throw "Install APK gagal."
    }
}

Write-Host "Membuka Miyako..."
& $adb -s $device shell am force-stop $packageName
& $adb -s $device shell am start -n $launcher
if ($LASTEXITCODE -ne 0) {
    throw "Aplikasi gagal dibuka."
}

Write-Host "Miyako berhasil dijalankan."
