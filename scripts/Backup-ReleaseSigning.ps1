param([Parameter(Mandatory = $true)][string]$Destination)
$ErrorActionPreference = 'Stop'
if (Test-Path -LiteralPath $Destination) { throw 'El destino ya existe. Elige un archivo nuevo.' }
. (Join-Path $PSScriptRoot 'Release-Signing.ps1')
try {
    $backupPassword = Read-Host 'Contraseña para el respaldo portable (mínimo 12 caracteres)' -AsSecureString
    if ($backupPassword.Length -lt 12) { throw 'La contraseña debe tener al menos 12 caracteres.' }
    $pointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($backupPassword)
    try {
        $env:FLIGHT_BACKUP_PASSWORD = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($pointer)
    } finally { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($pointer) }
    & (Join-Path $env:JAVA_HOME 'bin\keytool.exe') -importkeystore -noprompt `
        -srckeystore $env:FLIGHT_RELEASE_STORE_FILE -srcstoretype PKCS12 `
        -srcalias $env:FLIGHT_RELEASE_KEY_ALIAS `
        -srcstorepass:env FLIGHT_RELEASE_STORE_PASSWORD -srckeypass:env FLIGHT_RELEASE_KEY_PASSWORD `
        -destkeystore $Destination -deststoretype PKCS12 -destalias $env:FLIGHT_RELEASE_KEY_ALIAS `
        -deststorepass:env FLIGHT_BACKUP_PASSWORD -destkeypass:env FLIGHT_BACKUP_PASSWORD
    if ($LASTEXITCODE -ne 0) { throw 'No se pudo exportar el respaldo de firma.' }
} finally {
    $env:FLIGHT_RELEASE_STORE_PASSWORD = $null
    $env:FLIGHT_RELEASE_KEY_PASSWORD = $null
    $env:FLIGHT_BACKUP_PASSWORD = $null
}
