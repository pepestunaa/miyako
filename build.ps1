$ErrorActionPreference = "Stop"

$projectRoot = Split-Path -Parent $MyInvocation.MyCommand.Path

Push-Location $projectRoot
try {
    Write-Host "Membuat APK debug..."
    & ".\gradlew.bat" ":app:assembleStandardDebug" "-PdevAbi=x86_64" "--parallel" "--console=plain" "-x" "lint"
    if ($LASTEXITCODE -ne 0) {
        throw "Build gagal."
    }
} finally {
    Pop-Location
}

Write-Host "Build berhasil. Meng-install APK..."
& (Join-Path $projectRoot "install.ps1")
if ($LASTEXITCODE -ne 0) {
    throw "Install APK gagal."
}

Write-Host "Install berhasil. Membuka Miyako..."
& (Join-Path $projectRoot "launch.ps1")
if ($LASTEXITCODE -ne 0) {
    throw "Launch aplikasi gagal."
}

Write-Host "Build, install, dan launch berhasil."
