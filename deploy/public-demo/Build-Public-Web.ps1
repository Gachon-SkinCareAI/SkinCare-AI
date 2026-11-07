param(
    [Parameter(Mandatory=$true)][string]$ApiOrigin,
    [Parameter(Mandatory=$true)][string]$OutputDirectory,
    [string]$MobileDirectory
)
$ErrorActionPreference = 'Stop'
# Windows PowerShell 5.1 can bind parameter defaults before PSScriptRoot is set.
# Resolve the default only after entering the script body.
if ([string]::IsNullOrWhiteSpace($MobileDirectory)) {
    $MobileDirectory = Join-Path (Split-Path (Split-Path $PSScriptRoot)) 'mobile'
}
. (Join-Path $PSScriptRoot 'native_command.ps1')
$taskUri = [Uri]$ApiOrigin
if ($taskUri.Scheme -ne 'https' -or $taskUri.AbsolutePath -ne '/' -or $taskUri.Query -or $taskUri.Fragment -or $taskUri.UserInfo) { throw 'A public HTTPS origin is required.' }
if (Test-Path -LiteralPath $OutputDirectory) { throw 'Use a fresh output directory; exported files are never overwritten.' }
$taskNames = @('EXPO_PUBLIC_API_URL','SKINCARE_PUBLIC_DEMO_BUILD','SKINCARE_USB_TEST_BUILD','CI','EXPO_NO_TELEMETRY','EXPO_NO_DOTENV','NODE_ENV')
$taskOld = @{}
foreach ($taskName in $taskNames) { $taskOld[$taskName] = [Environment]::GetEnvironmentVariable($taskName,'Process') }
Push-Location -LiteralPath $MobileDirectory
try {
    $env:EXPO_PUBLIC_API_URL = $ApiOrigin.TrimEnd('/')
    Remove-Item Env:SKINCARE_PUBLIC_DEMO_BUILD,Env:SKINCARE_USB_TEST_BUILD -ErrorAction SilentlyContinue
    $env:CI='1'; $env:EXPO_NO_TELEMETRY='1'; $env:EXPO_NO_DOTENV='1'; $env:NODE_ENV='production'
    Invoke-TaskNative -File '.\node_modules\.bin\expo.cmd' -Arguments @('export','--platform','web','--output-dir',[IO.Path]::GetFullPath($OutputDirectory))
} finally {
    Pop-Location
    foreach ($taskName in $taskNames) { [Environment]::SetEnvironmentVariable($taskName,$taskOld[$taskName],'Process') }
}
