param(
    [string]$db = "data/app.db",
    [string]$src = "data/app.db.bak"
)

Write-Host "Restoring $src -> $db"
Copy-Item -Path $src -Destination $db -Force
Write-Host "Restore complete"
