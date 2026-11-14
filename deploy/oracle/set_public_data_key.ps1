# Replace the data.go.kr service key (PUBLIC_MEDICAL_SERVICE_KEY) on the cloud server.
# The key is typed here (hidden), tested on the server against the three public APIs
# BEFORE it is saved, and only then stored and the backend restarted.
# It is never shown, written to disk on this PC, or put on a command line.
#
# Usage (PowerShell):
#   powershell -ExecutionPolicy Bypass -File deploy\oracle\set_public_data_key.ps1
param(
    [string]$Server = $env:SKINCARE_SERVER,   # e.g. skincare@<VM-IP>; see deploy/oracle/README.md
    [string]$Key = $(if ($env:SKINCARE_SSH_KEY) { $env:SKINCARE_SSH_KEY } else { "$HOME\.ssh\oracle-skincare.key" }),
    [string]$Bundle = "/srv/skincare",
    [switch]$AllowPartial   # save even if some API is not approved yet
)
$ErrorActionPreference = "Stop"
if (-not $Server) { throw "Set the SKINCARE_SERVER environment variable (e.g. skincare@<VM-IP>) or pass -Server. See deploy/oracle/README.md." }
$here = Split-Path -Parent $MyInvocation.MyCommand.Path
$ssh = @("-i", $Key, "-o", "BatchMode=yes")

$secure = Read-Host "data.go.kr 일반 인증키 (Decoding 권장, 화면에 표시되지 않음)" -AsSecureString
$ptr = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure)
try { $value = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($ptr).Trim() }
finally { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($ptr) }
if ($value.Length -lt 20) { throw "Key is empty or too short." }
$json = (@{ PUBLIC_MEDICAL_SERVICE_KEY = $value } | ConvertTo-Json -Compress)
$value = $null

Write-Host "== uploading helpers"
& scp -q @ssh "$here\update_secret.py" "$here\check_public_data.py" "${Server}:$Bundle/"
if ($LASTEXITCODE) { throw "scp failed" }

Write-Host "== testing the new key against the public APIs (nothing saved yet)"
$json | & ssh @ssh $Server "python3 $Bundle/check_public_data.py"
$checkFailed = [bool]$LASTEXITCODE
if ($checkFailed -and -not $AllowPartial) {
    $json = $null
    throw "Not saved: approve the failing API(s) on data.go.kr (approval can take 1-2 hours to apply), then run again."
}

Write-Host "== saving key on the server"
$json | & ssh @ssh $Server "sudo python3 $Bundle/update_secret.py"
if ($LASTEXITCODE) { throw "update failed" }
$json = $null

Write-Host "== restarting backend (1-2 minutes)"
$compose = "docker compose --env-file .env -f compose.yaml -f compose.cloud.yaml -f compose.llm.yaml -f compose.llm-cpu.yaml --profile cloud-connector"
& ssh @ssh $Server "sudo systemctl stop skincare-ensure.timer 2>/dev/null; cd $Bundle && sudo $compose up -d --no-deps --force-recreate --wait --wait-timeout 300 backend; rc=`$?; sudo systemctl start skincare-ensure.timer 2>/dev/null; exit `$rc"
if ($LASTEXITCODE) { throw "backend restart failed - check: docker logs skincare-public-demo-backend-1" }

Write-Host "== re-check with the saved key"
& ssh @ssh $Server "sudo python3 $Bundle/check_public_data.py --current"
Write-Host "complete-public-data-key-update"
