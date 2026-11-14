# Set a social-login client (Google, Kakao or Naver) on the cloud server and
# restart only the backend. The secret is typed here (hidden) and sent over SSH
# stdin; it is never shown, written to disk on this PC, or put on a command line.
#
# Usage (PowerShell):
#   powershell -ExecutionPolicy Bypass -File deploy\oracle\set_social_oauth.ps1 -Provider kakao
param(
    [Parameter(Mandatory = $true)][ValidateSet("google", "kakao", "naver")][string]$Provider,
    [string]$Server = $env:SKINCARE_SERVER,   # e.g. skincare@<VM-IP>; see deploy/oracle/README.md
    [string]$Key = $(if ($env:SKINCARE_SSH_KEY) { $env:SKINCARE_SSH_KEY } else { "$HOME\.ssh\oracle-skincare.key" }),
    [string]$Bundle = "/srv/skincare"
)
$ErrorActionPreference = "Stop"
if (-not $Server) { throw "Set the SKINCARE_SERVER environment variable (e.g. skincare@<VM-IP>) or pass -Server. See deploy/oracle/README.md." }
$here = Split-Path -Parent $MyInvocation.MyCommand.Path
$ssh = @("-i", $Key, "-o", "BatchMode=yes")

$spec = @{
    google = @{ Id = "GOOGLE_CLIENT_ID"; Secret = "GOOGLE_SECRET"
                IdLabel = "Google OAuth Client ID"; SecretLabel = "Google OAuth Client secret"
                Pattern = '^[0-9]+-[a-z0-9]+\.apps\.googleusercontent\.com$'
                Hint = "...apps.googleusercontent.com" }
    kakao  = @{ Id = "KAKAO_CLIENT_ID"; Secret = "KAKAO_CLIENT_SECRET"
                IdLabel = "Kakao REST API key"; SecretLabel = "Kakao Client Secret"
                Pattern = '^[0-9a-f]{32}$'
                Hint = "32 lowercase hex characters (REST API key, not the JavaScript/Native key)" }
    naver  = @{ Id = "NAVER_CLIENT_ID"; Secret = "NAVER_CLIENT_SECRET"
                IdLabel = "Naver Client ID"; SecretLabel = "Naver Client Secret"
                Pattern = '^[A-Za-z0-9_-]{10,40}$'
                Hint = "the Client ID shown in Naver Developers" }
}[$Provider]

$id = (Read-Host $spec.IdLabel).Trim()
if ($id -notmatch $spec.Pattern) { throw "That does not look right. Expected: $($spec.Hint)" }
$secure = Read-Host "$($spec.SecretLabel) (hidden)" -AsSecureString
$ptr = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure)
try { $secret = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($ptr).Trim() }
finally { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($ptr) }
if ($secret.Length -lt 8) { throw "Secret is empty or too short." }
if ($secret -eq $id) { throw "The secret is the same as the ID - paste the Client Secret, not the key." }

Write-Host "== uploading helper"
& scp -q @ssh "$here\update_secret.py" "${Server}:$Bundle/update_secret.py"
if ($LASTEXITCODE) { throw "scp failed" }

Write-Host "== updating server config ($Provider)"
$json = (@{ $spec.Id = $id; $spec.Secret = $secret } | ConvertTo-Json -Compress)
$json | & ssh @ssh $Server "sudo python3 $Bundle/update_secret.py"
if ($LASTEXITCODE) { throw "update failed" }
$secret = $null; $json = $null

Write-Host "== restarting backend (1-2 minutes)"
$compose = "docker compose --env-file .env -f compose.yaml -f compose.cloud.yaml -f compose.llm.yaml -f compose.llm-cpu.yaml --profile cloud-connector"
# Pause the 5-minute health timer: if it fires while the backend restarts it sees
# the address as down and needlessly rebuilds the tunnel (about a minute of 503s).
& ssh @ssh $Server "sudo systemctl stop skincare-ensure.timer 2>/dev/null; cd $Bundle && sudo $compose up -d --no-deps --force-recreate --wait --wait-timeout 300 backend; rc=`$?; sudo systemctl start skincare-ensure.timer 2>/dev/null; exit `$rc"
if ($LASTEXITCODE) { throw "backend restart failed - check: docker logs skincare-public-demo-backend-1" }

$providers = Invoke-RestMethod "https://skincare-ai.skincare-server.workers.dev/api/wc/auth/social/providers"
$entry = $providers | Where-Object { $_.id -eq $Provider }
Write-Host "$Provider login enabled: $($entry.enabled)"
Write-Host "complete-$Provider-oauth-update"
