param(
    [string]$db = "data/app.db",
    [string]$out = "data/app.db.bak"
)

# Ensure parent directory exists
$dir = Split-Path $out -Parent
if (-not (Test-Path $dir)) { New-Item -ItemType Directory -Path $dir | Out-Null }

Write-Host "Backing up $db -> $out"
# Try to run a checkpoint via sqlite3.exe if available, otherwise just copy
try {
    & sqlite3.exe $db "PRAGMA wal_checkpoint(TRUNCATE);" | Out-Null
} catch {
    # ignore
}
Copy-Item -Path $db -Destination $out -Force
Write-Host "Backup complete"
