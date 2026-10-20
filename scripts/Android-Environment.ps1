$ErrorActionPreference = 'Stop'

$javaCandidates = @(
    $env:JAVA_HOME,
    (Join-Path $env:ProgramFiles 'Android\Android Studio\jbr'),
    (Join-Path $env:ProgramFiles 'Java\jdk-21')
)
$javaDirectory = $javaCandidates | Where-Object {
    $_ -and (Test-Path -LiteralPath (Join-Path $_ 'bin\javac.exe'))
} | Select-Object -First 1
if (-not $javaDirectory) { throw 'Instala JDK 21 o configura JAVA_HOME con un JDK válido.' }
$env:JAVA_HOME = $javaDirectory

$sdkCandidates = @($env:ANDROID_HOME, $env:ANDROID_SDK_ROOT, (Join-Path $env:LOCALAPPDATA 'Android\Sdk'))
$sdkDirectory = $sdkCandidates | Where-Object {
    $_ -and (Test-Path -LiteralPath (Join-Path $_ 'platforms'))
} | Select-Object -First 1
if (-not $sdkDirectory) { throw 'Configura ANDROID_HOME con el Android SDK.' }
$env:ANDROID_HOME = $sdkDirectory
$env:ANDROID_SDK_ROOT = $sdkDirectory

# Keep Android Studio's SDK location in the project-local file using the
# escaped Windows path expected by the Android Gradle Plugin.
$escapedSdkDirectory = $sdkDirectory.Replace('\', '/').Replace(':', '\:')
[System.IO.File]::WriteAllText(
    (Join-Path (Split-Path $PSScriptRoot -Parent) 'local.properties'),
    "sdk.dir=$escapedSdkDirectory",
    [System.Text.UTF8Encoding]::new($false)
)

# A dedicated cache avoids modifying caches used by unrelated Android projects.
if (-not $env:GRADLE_USER_HOME) {
    $env:GRADLE_USER_HOME = Join-Path $env:USERPROFILE '.gradle-flight-notifications'
}
