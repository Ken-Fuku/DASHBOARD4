param(
    [string]$AppCoreDir = "app-core",
    [int]$Port = 9191
)

# Simple wrapper to run full test suite (including E2E) locally in PowerShell
cd $AppCoreDir
Write-Host "Running mvn test (E2E enabled)..."
mvn test
