param(
    # Default to the repository root `data/app.db` when script is run from the module folder.
    [string]$DbPath = "../data/app.db",
    [switch]$InstallAppCore
)

Write-Host "Preparing to run DASHBOARD4 JavaFX UI..."

$logs = Join-Path -Path (Get-Location) -ChildPath "target/logs"
if (!(Test-Path $logs)) {
    Write-Host "Creating logs directory: $logs"
    New-Item -ItemType Directory -Path $logs | Out-Null
}

if ($InstallAppCore) {
    Write-Host "Installing app-core into local Maven repository..."
    mvn -pl app-core -am -DskipTests install
}

Write-Host "Starting JavaFX app (this will download JavaFX plugin if needed)..."
# Pass the chosen DB path into the JVM so ApiClient can pick it up via System.getProperty("db.path")
# Use PowerShell argument quoting so Maven receives a single -D argument even when path contains spaces
$dbArg = "-Ddb.path=$DbPath"

# Ensure module-local DB has schema by copying repo root DB when module DB is missing or empty
$repoRoot = Get-Location
$moduleDb = Join-Path $repoRoot "dashboard4-ui-javafx\data\app.db"
$repoDb = Join-Path $repoRoot "data\app.db"
if (Test-Path $repoDb) {
    $needCopy = $false
    if (!(Test-Path $moduleDb)) { $needCopy = $true }
    else {
        $len = (Get-Item $moduleDb).Length
        if ($len -eq 0) { $needCopy = $true }
    }
    if ($needCopy) {
        Write-Host "Copying repo DB ($repoDb) -> module DB ($moduleDb)"
        $moduleDir = Split-Path $moduleDb -Parent
        if (!(Test-Path $moduleDir)) { New-Item -ItemType Directory -Path $moduleDir | Out-Null }
        Copy-Item -Path $repoDb -Destination $moduleDb -Force
    }
}

& mvn -pl dashboard4-ui-javafx org.openjfx:javafx-maven-plugin:0.0.8:run $dbArg
