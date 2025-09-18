param(
    [int]$Count = 10000,
    [string]$OutFile = "data/bench-changelog.jsonl",
    [string]$DbPath = "data/bench.db",
    [int]$BatchSize = 100,
    [switch]$Clean
)

# Normalize paths relative to script location when relative paths are provided
$scriptRoot = Split-Path -Parent $MyInvocation.MyCommand.Definition
if (-not ([System.IO.Path]::IsPathRooted($OutFile))) { $OutFile = Join-Path (Resolve-Path "$scriptRoot\..\.." ) $OutFile }
if (-not ([System.IO.Path]::IsPathRooted($DbPath))) { $DbPath = Join-Path (Resolve-Path "$scriptRoot\..\.." ) $DbPath }

# clean option: remove existing files if requested (do this before generation)
if ($Clean) {
    if (Test-Path $OutFile) { Remove-Item $OutFile -Force }
    if (Test-Path $DbPath) { Remove-Item $DbPath -Force }
}

# generate simple JSONL changelog with header into memory and write without BOM
$header = @{"__meta__" = @{ "client_id" = "bench-client"; "source_node" = "bench" }} | ConvertTo-Json -Compress
$lines = New-Object System.Collections.Generic.List[System.String]
$lines.Add($header) | Out-Null
for ($i=1; $i -le $Count; $i++) {
    $entry = @{ lsn = $i; tx_id = "tx-$i"; table_name = "bench_table"; pk_json = @{ id = $i } | ConvertTo-Json -Compress; op = "I"; payload = @{ id = $i; name = "name-$i" } | ConvertTo-Json -Compress; tombstone = 0; created_at = (Get-Date).ToString("yyyy-MM-dd HH:mm:ss"); source_node = "bench" }
    $line = ($entry | ConvertTo-Json -Compress)
    $lines.Add($line) | Out-Null
}

# write UTF-8 without BOM
[System.IO.File]::WriteAllLines($OutFile, $lines.ToArray(), (New-Object System.Text.UTF8Encoding($false)))

Write-Host "Generated $Count entries into $OutFile"

# create logs dirs expected by SimpleLogger to avoid FileNotFound warnings
$scriptRoot = Split-Path -Parent $MyInvocation.MyCommand.Definition
$repoRoot = (Resolve-Path "$scriptRoot\..\..").Path
$logsDir1 = Join-Path $repoRoot "target\logs"
$logsDir2 = Join-Path $repoRoot "app-core\target\logs"
foreach ($d in @($logsDir1, $logsDir2)) {
    if (-not (Test-Path $d)) { New-Item -ItemType Directory -Path $d | Out-Null }
}

# create DB schema via BenchMigrate then run import
# use explicit argument array when invoking java to avoid PowerShell parsing ambiguities
$benchClassPath = "app-core/target/classes;app-core/target/dependency/*"
& java @("-cp", $benchClassPath, "com.kenfukuda.dashboard.cli.BenchMigrate")
Write-Host "Importing with batchSize=$BatchSize"
$args = @("-Dimport.batchSize=$BatchSize", "-cp", $benchClassPath, "com.kenfukuda.dashboard.cli.RunCli", "import-changelog", "--db", $DbPath, "--in", $OutFile)
& java @args

Write-Host "Import completed"
