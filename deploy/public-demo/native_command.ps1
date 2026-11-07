# Windows PowerShell 5 wraps native stderr in ErrorRecord objects. An advisory
# on stderr must not terminate a successful build under ErrorActionPreference=Stop.
function Invoke-TaskNative {
    param([Parameter(Mandatory=$true)][string]$File, [string[]]$Arguments=@(), [switch]$Capture)
    $taskOldPreference=$ErrorActionPreference
    try {
        $ErrorActionPreference='Continue'
        if($Capture){
            $taskOutput=@(& $File @Arguments 2>&1)
            $taskCode=$LASTEXITCODE
        } else {
            & $File @Arguments 2>&1 | ForEach-Object { Write-Host $_.ToString() }
            $taskCode=$LASTEXITCODE
        }
    } finally { $ErrorActionPreference=$taskOldPreference }
    if($null -eq $taskCode -or $taskCode -ne 0){throw "Native command failed with exit code $taskCode ($File)."}
    if($Capture){return (($taskOutput|ForEach-Object {$_.ToString()}) -join "`n")}
}

function Get-TaskCertificateDigest {
    param([Parameter(Mandatory=$true)][string]$Output)
    # apksigner versions label the same certificate as either "Signer #1" or
    # "V2 Signer:". Compare the digest, not a tool-version-dependent prefix.
    $taskMatches=[regex]::Matches($Output,'(?im)^[^\r\n]*certificate SHA-256 digest:[ \t]*([0-9a-f]{64})[ \t]*\r?$')
    $taskDigests=@($taskMatches | ForEach-Object { $_.Groups[1].Value.ToLowerInvariant() } | Select-Object -Unique)
    if($taskDigests.Count -ne 1){throw 'Expected exactly one verified APK signing certificate SHA-256 digest.'}
    return $taskDigests[0]
}
