param(
    [int[]]$BatchSizes = @(50,100,200,500),
    [int]$Count = 10000,
    [string]$OutDir = "bench_results",
    [string]$OutFile = "data/bench-changelog.jsonl",
    [string]$DbPath = "data/bench.db",
    [switch]$Clean
)

$scriptRoot = Split-Path -Parent $MyInvocation.MyCommand.Definition
if (-not (Test-Path $OutDir)) { New-Item -ItemType Directory -Path $OutDir | Out-Null }

# Normalize relative paths to repo root
if (-not ([System.IO.Path]::IsPathRooted($OutFile))) { $OutFile = Join-Path (Resolve-Path "$scriptRoot\..\.." ) $OutFile }
if (-not ([System.IO.Path]::IsPathRooted($DbPath))) { $DbPath = Join-Path (Resolve-Path "$scriptRoot\..\.." ) $DbPath }

Write-Host "Starting bench variants: Count=$Count, OutDir=$OutDir, OutFile=$OutFile, DbPath=$DbPath, Clean=$Clean"

foreach ($b in $BatchSizes) {
    Write-Host "\n=== Running bench: batchSize=$b ==="

    # generate data and import using existing script; pass batch size and clean flag for first run
    $start = Get-Date
    $cleanFlag = ($Clean.IsPresent -and ($b -eq $BatchSizes[0]))
    $genScript = Join-Path $scriptRoot "generate_and_import.ps1"
    $genParams = @{ Count = $Count; OutFile = $OutFile; DbPath = $DbPath; BatchSize = $b }
    if ($cleanFlag) { $genParams['Clean'] = $true }
    & $genScript @genParams
    $end = Get-Date
    $elapsed = $end - $start

    $log = "batch=$b,took_ms=$([math]::Round($elapsed.TotalMilliseconds,2)),outfile=$OutFile,db=$DbPath"
    $logFile = Join-Path $OutDir "bench-results.csv"
    Add-Content -Path $logFile -Value $log
    Write-Host $log
}

Write-Host "Bench variants complete. Results in $OutDir"
