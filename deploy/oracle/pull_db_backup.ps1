# Copy the cloud server's DB backups (/srv/skincare/backups/db) to this PC.
#   powershell -ExecutionPolicy Bypass -File deploy\oracle\pull_db_backup.ps1
# Only files not already on the PC are downloaded. The dumps contain personal data:
# keep them out of Git and out of shared folders.
param(
    [string]$Server = $env:SKINCARE_SERVER,   # e.g. skincare@<VM-IP>; see deploy/oracle/README.md
    [string]$Key = $(if ($env:SKINCARE_SSH_KEY) { $env:SKINCARE_SSH_KEY } else { "$HOME\.ssh\oracle-skincare.key" }),
    [string]$Remote = "/srv/skincare/backups/db",
    # Default: <folder that contains the repo>\Backup\db (outside Git)
    [string]$Local = $(if ($env:SKINCARE_BACKUP_DIR) { $env:SKINCARE_BACKUP_DIR } else { [IO.Path]::GetFullPath((Join-Path $PSScriptRoot "..\..\..\Backup\db")) })
)
$ErrorActionPreference = "Stop"
if (-not $Server) { throw "Set the SKINCARE_SERVER environment variable (e.g. skincare@<VM-IP>) or pass -Server. See deploy/oracle/README.md." }
New-Item -ItemType Directory -Force $Local | Out-Null

$names = ssh -o BatchMode=yes -i $Key $Server "ls -1 $Remote/*.sql.gz 2>/dev/null | xargs -n1 basename"
if ($LASTEXITCODE -ne 0) { throw "Could not list backups on $Server" }

$new = @($names | Where-Object { $_ -and -not (Test-Path (Join-Path $Local $_)) })
if ($new.Count -eq 0) { Write-Host "Up to date: $Local"; exit 0 }

foreach ($name in $new) {
    $dest = Join-Path $Local $name
    scp -q -i $Key "${Server}:$Remote/$name" "$dest.partial"
    if ($LASTEXITCODE -ne 0) { Remove-Item -ErrorAction SilentlyContinue "$dest.partial"; throw "Download failed: $name" }
    Move-Item -Force "$dest.partial" $dest
    Write-Host ("Downloaded {0} ({1:N1} MB)" -f $name, ((Get-Item $dest).Length / 1MB))
}
