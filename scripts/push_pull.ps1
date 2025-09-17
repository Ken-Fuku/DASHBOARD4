param(
    [string]$db = "data/app.db",
    [string]$fromLsn = "0",
    [string]$outDir = "data",
    [string]$clientId = "client-local"
)

# create output filename
$timestamp = Get-Date -Format "yyyyMMddHHmmss"
$outFile = Join-Path $outDir ("changelog_export_$timestamp.jsonl")

# run export
Write-Host "Exporting from LSN $fromLsn to $outFile"
java -cp target/app-core-0.0.1-SNAPSHOT.jar com.kenfukuda.dashboard.cli.RunCli export-changelog --db $db --from-lsn $fromLsn --out $outFile

if ($LASTEXITCODE -ne 0) {
    Write-Error "Export failed with exit code $LASTEXITCODE"
    exit $LASTEXITCODE
}

# run import (local)
Write-Host "Importing $outFile with client_id=$clientId"
java -cp target/app-core-0.0.1-SNAPSHOT.jar com.kenfukuda.dashboard.cli.RunCli import-changelog --db $db --in $outFile --client-id $clientId

if ($LASTEXITCODE -ne 0) {
    Write-Error "Import failed with exit code $LASTEXITCODE"
    exit $LASTEXITCODE
}

Write-Host "Push/Pull completed: $outFile"
