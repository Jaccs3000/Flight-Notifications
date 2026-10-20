param(
    [switch]$SkipChecks,
    [string]$OutputDirectory
)
$ErrorActionPreference = 'Stop'
$projectDirectory = Split-Path $PSScriptRoot -Parent
if (-not $OutputDirectory) { $OutputDirectory = 'G:\Mi unidad\APK android car' }
elseif (-not [System.IO.Path]::IsPathRooted($OutputDirectory)) {
    $OutputDirectory = Join-Path $projectDirectory $OutputDirectory
}
. (Join-Path $PSScriptRoot 'Release-Signing.ps1')
Push-Location $projectDirectory
try {
    New-Item -ItemType Directory -Force -Path $OutputDirectory | Out-Null
    $versionName = [regex]::Match((Get-Content app\build.gradle.kts -Raw), 'versionName\s*=\s*"([^"]+)"').Groups[1].Value
    if (-not $versionName) { throw 'No se pudo leer versionName desde app/build.gradle.kts.' }
    $fileStem = "flight-notifications-$versionName"
    $workspaceOutput = Join-Path $projectDirectory 'outputs'
    New-Item -ItemType Directory -Force -Path $workspaceOutput | Out-Null
    $tasks = @(':app:assembleDebug', ':app:assembleRelease')
    if (-not $SkipChecks) { $tasks = @(':app:testDebugUnitTest', ':app:lintDebug', ':app:lintRelease') + $tasks }
    & .\gradlew.bat @tasks --project-cache-dir .gradle-flight --no-daemon --console=plain 2>&1 |
        Tee-Object -FilePath outputs\build-final.log
    if ($LASTEXITCODE -ne 0) { throw 'Gradle falló. Consulta outputs\build-final.log.' }
    Copy-Item app\build\outputs\apk\debug\app-debug.apk (Join-Path $OutputDirectory "$fileStem-debug.apk") -Force
    Copy-Item app\build\outputs\apk\release\app-release.apk (Join-Path $OutputDirectory "$fileStem-release.apk") -Force
    Copy-Item (Join-Path $OutputDirectory "$fileStem-debug.apk") (Join-Path $workspaceOutput "$fileStem-debug.apk") -Force
    Copy-Item (Join-Path $OutputDirectory "$fileStem-release.apk") (Join-Path $workspaceOutput "$fileStem-release.apk") -Force
    $hashManifest = Join-Path $OutputDirectory 'SHA256SUMS.txt'
    Get-FileHash -Algorithm SHA256 (Join-Path $OutputDirectory "$fileStem-*.apk") |
        ForEach-Object { '{0}  {1}' -f $_.Hash.ToLowerInvariant(), (Split-Path $_.Path -Leaf) } |
        Set-Content $hashManifest
    Copy-Item $hashManifest (Join-Path $workspaceOutput 'SHA256SUMS.txt') -Force
} finally {
    Pop-Location
    $env:FLIGHT_RELEASE_STORE_PASSWORD = $null
    $env:FLIGHT_RELEASE_KEY_PASSWORD = $null
}
