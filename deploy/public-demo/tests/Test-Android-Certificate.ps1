$ErrorActionPreference='Stop'
. (Join-Path (Split-Path $PSScriptRoot) 'native_command.ps1')
$taskDigest='ab'*32
$taskOld="Signer #1 certificate SHA-256 digest: $taskDigest"
$taskNew="V2 Signer: certificate SHA-256 digest: $($taskDigest.ToUpperInvariant())"
if((Get-TaskCertificateDigest $taskOld) -ne $taskDigest){throw 'Legacy signer format rejected.'}
if((Get-TaskCertificateDigest $taskNew) -ne $taskDigest){throw 'Current signer format rejected.'}
if((Get-TaskCertificateDigest ($taskNew+"`r`nV3 Signer: certificate SHA-256 digest: "+$taskDigest)) -ne $taskDigest){throw 'Same certificate across signing schemes rejected.'}
foreach($taskInvalid in @('No certificate in output',($taskOld+"`nSigner #2 certificate SHA-256 digest: "+('cd'*32)))){
    $taskRejected=$false
    try { Get-TaskCertificateDigest $taskInvalid | Out-Null } catch { $taskRejected=$true }
    if(-not $taskRejected){throw 'Missing or ambiguous certificate was accepted.'}
}
Write-Host 'PASS: 5 certificate parser cases, including fail-closed missing and multiple identities.'
