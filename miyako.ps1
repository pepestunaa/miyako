param(
    [ValidateSet("start", "build", "install", "launch", "stop", "run", "help")]
    [string]$Command = "help",
    [switch]$NoBuild
)

$ErrorActionPreference = "Stop"
$projectRoot = Split-Path -Parent $MyInvocation.MyCommand.Path

function Invoke-ProjectScript([string]$Name) {
    & (Join-Path $projectRoot "$Name.ps1")
    if ($LASTEXITCODE -ne 0) {
        throw "Perintah '$Name' gagal."
    }
}

switch ($Command) {
    "start" {
        Invoke-ProjectScript "start"
    }
    "build" {
        Invoke-ProjectScript "build"
    }
    "install" {
        Invoke-ProjectScript "install"
    }
    "launch" {
        Invoke-ProjectScript "launch"
    }
    "stop" {
        Invoke-ProjectScript "stop"
    }
    "run" {
        Invoke-ProjectScript "start"
        if ($NoBuild) {
            Invoke-ProjectScript "install"
        } else {
            Invoke-ProjectScript "build"
        }
        Invoke-ProjectScript "launch"
    }
    "help" {
        Write-Host "Penggunaan: miyako <perintah>"
        Write-Host ""
        Write-Host "Perintah:"
        Write-Host "  start    Menyalakan emulator miyako-dev"
        Write-Host "  build    Build debug dan install APK ke emulator"
        Write-Host "  install  Install APK terakhir ke emulator"
        Write-Host "  launch   Membuka aplikasi Miyako"
        Write-Host "  stop     Menghentikan emulator"
        Write-Host "  run      Start, build, install, dan launch"
        Write-Host ""
        Write-Host "Opsi run: -NoBuild memakai APK terakhir yang sudah dibuat"
    }
}
