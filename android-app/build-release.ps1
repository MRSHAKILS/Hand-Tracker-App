param([switch]$Offline)

$ErrorActionPreference = 'Stop'
$projectRoot = $PSScriptRoot
$signingDirectory = Join-Path $projectRoot '.signing'
$keyStorePath = Join-Path $signingDirectory 'finger-spark-release.p12'
$passwordPath = Join-Path $signingDirectory 'release-password.clixml'

$javaDirectory = $env:JAVA_HOME
if (-not $javaDirectory) {
    $localJava = Get-ChildItem -LiteralPath (Join-Path $projectRoot '.toolchain/jdk17') -Directory -ErrorAction SilentlyContinue |
        Where-Object { Test-Path (Join-Path $_.FullName 'bin/keytool.exe') } |
        Select-Object -First 1
    if ($localJava) { $javaDirectory = $localJava.FullName }
}
$keyTool = if ($javaDirectory) { Join-Path $javaDirectory 'bin/keytool.exe' } else { (Get-Command keytool -ErrorAction Stop).Source }

if ((Test-Path $keyStorePath) -ne (Test-Path $passwordPath)) {
    throw 'Signing files are incomplete. Restore the existing key and password; do not generate a replacement.'
}

$previousKeyStore = $env:FINGER_SPARK_KEYSTORE
$previousPassword = $env:FINGER_SPARK_STORE_PASSWORD
try {
    if (-not (Test-Path $keyStorePath)) {
        New-Item -ItemType Directory -Path $signingDirectory -Force | Out-Null
        $passwordBytes = New-Object byte[] 32
        $random = [Security.Cryptography.RandomNumberGenerator]::Create()
        try { $random.GetBytes($passwordBytes) } finally { $random.Dispose() }
        $securePassword = ConvertTo-SecureString ([Convert]::ToBase64String($passwordBytes)) -AsPlainText -Force
        $securePassword | Export-Clixml -LiteralPath $passwordPath
        $env:FINGER_SPARK_STORE_PASSWORD = [Net.NetworkCredential]::new('', $securePassword).Password
        & $keyTool -genkeypair -noprompt -storetype PKCS12 -keystore $keyStorePath -alias fingerspark -keyalg RSA -keysize 3072 -validity 10000 -dname 'CN=Finger Spark' -storepass:env FINGER_SPARK_STORE_PASSWORD -keypass:env FINGER_SPARK_STORE_PASSWORD
        if ($LASTEXITCODE -ne 0) { throw 'Could not create release signing key. Inspect and restore signing files before retrying.' }
    } else {
        $securePassword = Import-Clixml -LiteralPath $passwordPath
        $env:FINGER_SPARK_STORE_PASSWORD = [Net.NetworkCredential]::new('', $securePassword).Password
    }
    $env:FINGER_SPARK_KEYSTORE = $keyStorePath
    Push-Location $projectRoot
    try {
        $gradleArguments = @(':app:assembleRelease', ':app:lintRelease', ':app:testReleaseUnitTest', '--no-daemon')
        if ($Offline) { $gradleArguments += '--offline' }
        & (Join-Path $projectRoot 'gradlew.bat') @gradleArguments
        if ($LASTEXITCODE -ne 0) { throw 'Release build or verification failed.' }
    } finally {
        Pop-Location
    }
} finally {
    $env:FINGER_SPARK_KEYSTORE = $previousKeyStore
    $env:FINGER_SPARK_STORE_PASSWORD = $previousPassword
    $securePassword = $null
}

Write-Output 'Signed APK: app/build/outputs/apk/release/app-release.apk'
Write-Output 'Preserve .signing securely for future updates. The encrypted password is tied to this Windows account.'
