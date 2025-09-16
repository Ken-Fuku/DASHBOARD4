$depDir = Join-Path $PSScriptRoot 'app-core\target\dependency'
$jar = Join-Path $PSScriptRoot 'target\app-core-0.0.1-SNAPSHOT.jar'

# Ensure logs directory exists
$logsDir = Join-Path $PSScriptRoot 'target\logs'
if (-not (Test-Path $logsDir)) { New-Item -ItemType Directory -Path $logsDir | Out-Null }

# Collect dependency jars but exclude logback jars to avoid multiple SLF4J bindings
$deps = @()
if (Test-Path $depDir) {
	$deps = Get-ChildItem -Path $depDir -Filter '*.jar' | Where-Object { $_.Name -notmatch 'logback-classic|logback-core' } | ForEach-Object { $_.FullName }
}

# Put slf4j-api first if present
$slf = $deps | Where-Object { $_ -match 'slf4j-api' }
if ($slf) { $others = $deps | Where-Object { $_ -notmatch 'slf4j-api' }; $ordered = ,$slf + $others } else { $ordered = $deps }
$cp = $jar
if ($ordered) { $cp = $cp + ';' + ($ordered -join ';') }
Write-Host "Classpath: $cp"

# Run Java from the module directory so relative paths in simplelogger.properties (target/logs/...) resolve correctly
Push-Location $PSScriptRoot
try {
	java -cp $cp com.kenfukuda.dashboard.cli.MigrateMain
} finally {
	Pop-Location
}
