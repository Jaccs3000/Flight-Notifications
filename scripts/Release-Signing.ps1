$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'Android-Environment.ps1')

$signingDirectory = Join-Path $env:USERPROFILE '.android\flight-notifications-signing'
$storeFile = Join-Path $signingDirectory 'release.p12'
$passwordFile = Join-Path $signingDirectory 'password.dpapi'
New-Item -ItemType Directory -Force -Path $signingDirectory | Out-Null

# Keep the signing key and its DPAPI-protected password private to this Windows user.
$userSid = [System.Security.Principal.WindowsIdentity]::GetCurrent().User
& icacls.exe $signingDirectory /inheritance:r /grant:r `
    ('*{0}:(OI)(CI)F' -f $userSid.Value) '*S-1-5-18:(OI)(CI)F' | Out-Null
if ($LASTEXITCODE -ne 0) { throw 'No se pudo proteger la carpeta de firma.' }

if (-not (Test-Path -LiteralPath $passwordFile)) {
    if (Test-Path -LiteralPath $storeFile) {
        throw 'Existe una firma sin su contraseña protegida. Recupera la copia original; no se reemplazará.'
    }
    $randomBytes = New-Object byte[] 32
    $rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
    try { $rng.GetBytes($randomBytes) } finally { $rng.Dispose() }
    $securePassword = ConvertTo-SecureString ([Convert]::ToBase64String($randomBytes)) -AsPlainText -Force
    ConvertFrom-SecureString $securePassword | Set-Content -LiteralPath $passwordFile
    [Array]::Clear($randomBytes, 0, $randomBytes.Length)
}

$securePassword = (Get-Content -LiteralPath $passwordFile -Raw).Trim() | ConvertTo-SecureString
$passwordPointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($securePassword)
try {
    $env:FLIGHT_RELEASE_STORE_PASSWORD = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($passwordPointer)
    $env:FLIGHT_RELEASE_KEY_PASSWORD = $env:FLIGHT_RELEASE_STORE_PASSWORD
} finally {
    [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($passwordPointer)
}
$env:FLIGHT_RELEASE_STORE_FILE = $storeFile
$env:FLIGHT_RELEASE_KEY_ALIAS = 'flight-notifications'

if (-not (Test-Path -LiteralPath $storeFile)) {
    & (Join-Path $env:JAVA_HOME 'bin\keytool.exe') -genkeypair -noprompt `
        -keystore $storeFile -storetype PKCS12 -alias $env:FLIGHT_RELEASE_KEY_ALIAS `
        -storepass:env FLIGHT_RELEASE_STORE_PASSWORD -keypass:env FLIGHT_RELEASE_KEY_PASSWORD `
        -keyalg RSA -keysize 3072 -validity 10000 `
        -dname 'CN=Flight Notifications, OU=Personal Android App'
    if ($LASTEXITCODE -ne 0) { throw 'No se pudo crear la firma release.' }
}
