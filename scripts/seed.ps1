# seed.ps1 - execute seed.sql and create visit_daily 100 rows using sqlite3
$scriptRoot = Split-Path -Parent $MyInvocation.MyCommand.Definition
$root = Resolve-Path "$scriptRoot\.."
$appCore = Join-Path $root 'app-core'
$dataDir = Join-Path $root 'data'
$dbFile = Join-Path $dataDir 'app.db'
$logsDir = Join-Path $appCore 'target\logs'
$seedLog = Join-Path $logsDir 'seed.log'
$seedResult = Join-Path $root 'tools\seed_result.txt'

if (-not (Test-Path $logsDir)) { New-Item -ItemType Directory -Path $logsDir | Out-Null }
if (-not (Test-Path $dataDir)) { New-Item -ItemType Directory -Path $dataDir | Out-Null }

# prepare clean seed log (overwrite existing)
"Seed run started at $(Get-Date)" | Out-File -FilePath $seedLog -Encoding UTF8

# Run migrations first (uses app-core/run_migrate.ps1)
& "$appCore\run_migrate.ps1" 2>&1 | Tee-Object -FilePath $seedLog -Append

# Execute static seed SQL
# Requires sqlite3 available in PATH
sqlite3 "$dbFile" ".read '$scriptRoot\seed.sql'" 2>&1 | Tee-Object -FilePath $seedLog -Append

# Insert 100 visit_daily rows: distribute among 5 stores
for ($i = 1; $i -le 100; $i++) {
    $storeId = (($i - 1) % 5) + 1
    $date = "2025-09-" + ((($i - 1) % 30) + 1).ToString("00")
    $vis = (Get-Random -Minimum 10 -Maximum 500)
    $cmd = "INSERT INTO visit_daily (store_id, visit_date, visitors) VALUES ($storeId, '$date', $vis);"
    sqlite3 "$dbFile" "$cmd" 2>> $seedLog
}

# Run verification queries
sqlite3 "$dbFile" "SELECT count(*) FROM visit_daily;" > $seedResult
sqlite3 "$dbFile" "SELECT count(*) FROM company;" >> $seedResult
sqlite3 "$dbFile" "SELECT count(*) FROM store;" >> $seedResult
sqlite3 "$dbFile" "SELECT count(*) FROM budget_monthly;" >> $seedResult
sqlite3 "$dbFile" "SELECT count(*) FROM budget_factors;" >> $seedResult

# Append final message to seed.log
"Seed completed at $(Get-Date)" | Out-File -FilePath $seedLog -Append

Write-Host "Seed finished. Results in $seedResult, log in $seedLog"
