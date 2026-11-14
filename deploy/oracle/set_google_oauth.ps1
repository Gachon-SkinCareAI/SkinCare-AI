# Kept for the earlier instructions; same as: set_social_oauth.ps1 -Provider google
& (Join-Path (Split-Path -Parent $MyInvocation.MyCommand.Path) "set_social_oauth.ps1") -Provider google @args
