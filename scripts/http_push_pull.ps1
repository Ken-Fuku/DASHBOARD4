param(
    [string]$Server = "http://localhost:8080",
    [string]$Db = "data/app.db",
    [string]$ClientId = "client-local",
    [long]$FromLsn = 0,
    [string]$AuthToken
)

# Pull
$pullUrl = "$Server/sync/pull?from_lsn=$FromLsn"
Write-Host "Pulling from $pullUrl"
$headers = @{}
if ($AuthToken) { $headers.Add("Authorization", "Bearer $AuthToken") }
$response = Invoke-WebRequest -Uri $pullUrl -Headers $headers -Method GET -UseBasicParsing -ErrorAction Stop
$outFile = Join-Path -Path (Get-Location) -ChildPath ("changelog_pull_{0}.jsonl" -f (Get-Date -Format "yyyyMMddHHmmss"))
$response.Content | Out-File -FilePath $outFile -Encoding UTF8
Write-Host "Pulled to $outFile"

# Push
$pushUrl = "$Server/sync/push"
Write-Host "Pushing $outFile to $pushUrl"
$headers = @{}
if ($AuthToken) { $headers.Add("Authorization", "Bearer $AuthToken") }
Invoke-WebRequest -Uri $pushUrl -Headers $headers -Method POST -InFile $outFile -ContentType "application/x-ndjson" -UseBasicParsing -ErrorAction Stop
Write-Host "Push completed"
