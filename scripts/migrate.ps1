# migrate.ps1 - wrapper to run existing migration runner
$scriptRoot = Split-Path -Parent $MyInvocation.MyCommand.Definition
# call existing run_migrate.ps1 in app-core
& "$scriptRoot\..\app-core\run_migrate.ps1"
