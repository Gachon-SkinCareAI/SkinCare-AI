param(
    [Parameter(Mandatory=$true)][string]$ApiOrigin,
    [Parameter(Mandatory=$true)][string]$SigningDirectory,
    [Parameter(Mandatory=$true)][string]$OutputDirectory,
    [int]$VersionCode = 2
)
$ErrorActionPreference = 'Stop'
$taskRepo = Split-Path (Split-Path $PSScriptRoot)
$taskMobile = Join-Path $taskRepo 'mobile'
$taskSigning = [IO.Path]::GetFullPath($SigningDirectory)
$taskOutput = [IO.Path]::GetFullPath($OutputDirectory)
if ($taskSigning -eq $taskRepo -or $taskSigning.StartsWith($taskRepo+[IO.Path]::DirectorySeparatorChar,[StringComparison]::OrdinalIgnoreCase)) { throw 'Keep the signing directory outside the source repository.' }
if ($taskSigning.TrimEnd('\') -eq [IO.Path]::GetPathRoot($taskSigning).TrimEnd('\')) { throw 'Choose a dedicated signing subdirectory, not a drive root.' }
$taskConfigFile=Join-Path $taskSigning 'signing.json'
$taskKeyFile=Join-Path $taskSigning 'skincare-public.p12'
$taskCreatedSigning=$false
if ((Test-Path $taskSigning) -and @(Get-ChildItem -LiteralPath $taskSigning -Force).Count -gt 0) {
    if (-not (Test-Path $taskConfigFile)) { throw 'Use a dedicated empty signing directory or the existing SkinCare signing directory.' }
    $taskExisting=Get-Content -LiteralPath $taskConfigFile -Raw|ConvertFrom-Json
    if ($taskExisting.format -ne 'skincare-public-signing-v1') { throw 'This directory is not a managed SkinCare signing directory.' }
}
$taskNames = @('SKINCARE_PUBLIC_DEMO_BUILD','SKINCARE_USB_TEST_BUILD','EXPO_PUBLIC_API_URL','SKINCARE_ANDROID_VERSION_CODE','SKINCARE_ANDROID_KEYSTORE','SKINCARE_ANDROID_STORE_PASSWORD','SKINCARE_ANDROID_KEY_ALIAS','SKINCARE_ANDROID_KEY_PASSWORD','NODE_ENV','CI','EXPO_NO_TELEMETRY')
$taskOld = @{}
foreach ($taskName in $taskNames) { $taskOld[$taskName]=[Environment]::GetEnvironmentVariable($taskName,'Process') }
Push-Location $taskMobile
try {
    $env:SKINCARE_PUBLIC_DEMO_BUILD='1'
    Remove-Item Env:SKINCARE_USB_TEST_BUILD -ErrorAction SilentlyContinue
    $env:EXPO_PUBLIC_API_URL=$ApiOrigin.TrimEnd('/')
    $env:SKINCARE_ANDROID_VERSION_CODE=[string]$VersionCode
    $env:CI='1'; $env:EXPO_NO_TELEMETRY='1'; $env:NODE_ENV='production'
    & node -e "require('./app.config.js')({config:require('./app.json').expo})"
    if ($LASTEXITCODE -ne 0) { throw 'Public API origin or build profile is invalid.' }
    $taskProviders=Invoke-RestMethod ($env:EXPO_PUBLIC_API_URL+'/api/wc/auth/social/providers') -TimeoutSec 10
    if (@($taskProviders | Where-Object {$_.enabled}).Count -ne 3) { throw 'All three social providers must be enabled on the HTTPS server before this build.' }
    $taskPolicies=Invoke-RestMethod ($env:EXPO_PUBLIC_API_URL+'/api/wc/auth/consent-policy') -TimeoutSec 10
    if (-not $taskPolicies.requiredAvailable) { throw 'Publish the required policy documents before this build.' }
    if (-not $env:JAVA_HOME -or -not $env:ANDROID_HOME -or -not $env:GRADLE_USER_HOME) { throw 'Set JAVA_HOME, ANDROID_HOME and GRADLE_USER_HOME to your build tool directories.' }
    New-Item -ItemType Directory -Force -Path $taskSigning,$taskOutput | Out-Null
    $taskSid=([Security.Principal.WindowsIdentity]::GetCurrent()).User.Value
    & icacls $taskSigning /inheritance:r /grant:r ('*'+$taskSid+':(OI)(CI)F') '*S-1-5-18:(OI)(CI)F' | Out-Null
    if ($LASTEXITCODE -ne 0) { throw 'Could not protect the signing directory.' }
    if (-not (Test-Path $taskConfigFile)) {
        if (Test-Path $taskKeyFile) { throw 'Existing signing key has no password config. Restore its config; do not replace the key.' }
        $taskBytes=New-Object byte[] 32
        $taskRng=[Security.Cryptography.RandomNumberGenerator]::Create()
        $taskRng.GetBytes($taskBytes); $taskRng.Dispose()
        $taskPassword=([BitConverter]::ToString($taskBytes)).Replace('-','').ToLowerInvariant()
        @{format='skincare-public-signing-v1';alias='skincare-public';password=$taskPassword} | ConvertTo-Json | Set-Content -LiteralPath $taskConfigFile -Encoding UTF8
        $taskCreatedSigning=$true
    }
    $taskKeys=Get-Content -LiteralPath $taskConfigFile -Raw|ConvertFrom-Json
    $env:SKINCARE_ANDROID_KEYSTORE=$taskKeyFile
    $env:SKINCARE_ANDROID_STORE_PASSWORD=$taskKeys.password
    $env:SKINCARE_ANDROID_KEY_PASSWORD=$taskKeys.password
    $env:SKINCARE_ANDROID_KEY_ALIAS=$taskKeys.alias
    if (-not (Test-Path $taskKeyFile)) {
        # A config created on this invocation can create a new key. Existing configs
        # require recovery if their key is missing, preventing accidental key rotation.
        if (-not $taskCreatedSigning) { throw 'Signing key is missing. Restore the existing key before building an update.' }
        & (Join-Path $env:JAVA_HOME 'bin\keytool.exe') -genkeypair -storetype PKCS12 -keystore $taskKeyFile -storepass:env SKINCARE_ANDROID_STORE_PASSWORD -keypass:env SKINCARE_ANDROID_KEY_PASSWORD -alias $taskKeys.alias -keyalg RSA -keysize 3072 -validity 10000 -dname 'CN=SkinCare AI Demo'
        if ($LASTEXITCODE -ne 0) { throw 'Release key creation failed.' }
    }
    $taskApk=Join-Path $taskOutput ('SkinCare-AI-public-'+$VersionCode+'.apk')
    if (Test-Path $taskApk) { throw 'This output version already exists. Choose a new version code or output directory.' }
    & .\node_modules\.bin\expo.cmd prebuild --platform android --no-install
    if ($LASTEXITCODE -ne 0) { throw 'Native prebuild failed.' }
    $taskSdk=$env:ANDROID_HOME.Replace('\','/')
    @("sdk.dir=$taskSdk","cmake.dir=$taskSdk/cmake/3.31.6") | Set-Content android\local.properties -Encoding ASCII
    Push-Location android
    try {
        & .\gradlew.bat ':app:assembleRelease' '-PreactNativeArchitectures=arm64-v8a' '-Pandroid.enableMinifyInReleaseBuilds=false' '--no-daemon' '--max-workers=2' '--console=plain'
        if ($LASTEXITCODE -ne 0) { throw 'Android release build failed.' }
        $taskSource='app\build\outputs\apk\release\app-release.apk'
        & (Join-Path $env:ANDROID_HOME 'build-tools\37.0.0\apksigner.bat') verify --verbose $taskSource
        if ($LASTEXITCODE -ne 0) { throw 'APK signature verification failed.' }
        Copy-Item -LiteralPath $taskSource -Destination $taskApk
        @{apiOrigin=$env:EXPO_PUBLIC_API_URL;versionCode=$VersionCode;package='com.skincareai.mobile';sha256=(Get-FileHash $taskApk -Algorithm SHA256).Hash;publicDeviceLoginVerified=$false} | ConvertTo-Json | Set-Content -LiteralPath ($taskApk+'.json') -Encoding UTF8
    } finally { Pop-Location }
    Write-Host ('Public API APK built: '+$taskApk)
    Write-Host 'Keep the private signing directory backed up for future updates. Test again over mobile data without USB.'
} finally {
    Pop-Location
    foreach ($taskName in $taskNames) { [Environment]::SetEnvironmentVariable($taskName,$taskOld[$taskName],'Process') }
}
