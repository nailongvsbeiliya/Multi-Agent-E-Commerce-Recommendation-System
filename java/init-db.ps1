param(
    [string]$DbHost = "localhost",
    [int]$DbPort = 5432,
    [string]$AdminUser = "postgres",
    [string]$AdminPassword = "root",
    [string]$AppDb = "postgres"
)

$ErrorActionPreference = "Stop"
# In PowerShell 7+, native stderr may be promoted to ErrorRecord and stop the script.
# We rely on psql exit code instead, so keep native stderr non-terminating.
$PSNativeCommandUseErrorActionPreference = $false

function Require-Psql {
    $psqlCmd = Get-Command psql -ErrorAction SilentlyContinue
    if ($psqlCmd) {
        $script:PsqlExe = $psqlCmd.Source
        return
    }

    $fallback = Get-ChildItem -Path "C:\Program Files\PostgreSQL" -Recurse -Filter psql.exe -ErrorAction SilentlyContinue |
        Where-Object { $_.FullName -match "\\bin\\psql\.exe$" } |
        Sort-Object FullName -Descending |
        Select-Object -First 1

    if ($fallback) {
        $script:PsqlExe = $fallback.FullName
        return
    }

    throw "psql is not found in PATH or default PostgreSQL install folders."
}

function Run-Psql {
    param(
        [string]$Database,
        [string[]]$PsqlArgs
    )
    $output = & $script:PsqlExe -h $DbHost -p $DbPort -U $AdminUser -d $Database @PsqlArgs 2>&1
    if ($output) {
        $output | ForEach-Object { Write-Host ($_.ToString()) }
    }
    if ($LASTEXITCODE -ne 0) {
        $details = ($output | Out-String).Trim()
        if ([string]::IsNullOrWhiteSpace($details)) {
            $details = "no stderr output"
        }
        throw "psql command failed (database=$Database, host=$DbHost, port=$DbPort, user=$AdminUser): $details"
    }
    return $output
}

function Resolve-ControlDatabase {
    $candidates = @("postgres", "template1")
    foreach ($candidate in $candidates) {
        try {
            Write-Host "[check] trying control database '$candidate'..."
            Run-Psql -Database $candidate -PsqlArgs @("-v", "ON_ERROR_STOP=1", "-c", "SELECT 1;") | Out-Null
            return $candidate
        } catch {
            Write-Host "[warn] cannot use '$candidate' as control database: $($_.Exception.Message)"
        }
    }
    throw "Unable to connect to control databases (postgres/template1). Check host/port/user/password."
}

Require-Psql
Write-Host "[startup] psql executable = $script:PsqlExe"

$scriptRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$sqlFile = Join-Path $scriptRoot "db\init_schema_and_demo.sql"
if (-not (Test-Path $sqlFile)) {
    throw "SQL file not found: $sqlFile"
}

$env:PGPASSWORD = $AdminPassword
$env:PGCLIENTENCODING = "UTF8"
try {
    Write-Host "[1/4] checking PostgreSQL connectivity..."
    $controlDb = Resolve-ControlDatabase
    Write-Host "[info] selected control database: $controlDb"
    Run-Psql -Database $controlDb -PsqlArgs @("-v", "ON_ERROR_STOP=1", "-c", "SELECT version();")

    Write-Host "[2/4] ensuring database '$AppDb' exists..."
    $exists = Run-Psql -Database $controlDb -PsqlArgs @("-tAc", "SELECT 1 FROM pg_database WHERE datname = '$AppDb';")

    if (($exists | Out-String).Trim() -ne "1") {
        Run-Psql -Database $controlDb -PsqlArgs @("-v", "ON_ERROR_STOP=1", "-c", "CREATE DATABASE `"$AppDb`";")
        Write-Host "Database created: $AppDb"
    } else {
        Write-Host "Database already exists: $AppDb"
    }

    Write-Host "[3/4] applying schema and demo data..."
    Run-Psql -Database $AppDb -PsqlArgs @("-v", "ON_ERROR_STOP=1", "-f", $sqlFile)

    Write-Host "[4/4] final row count check..."
    Run-Psql -Database $AppDb -PsqlArgs @("-v", "ON_ERROR_STOP=1", "-c", "SELECT COUNT(*) AS product_catalog_rows FROM product_catalog;")
    Run-Psql -Database $AppDb -PsqlArgs @("-v", "ON_ERROR_STOP=1", "-c", "SELECT COUNT(*) AS inventory_rows FROM inventory;")
    Run-Psql -Database $AppDb -PsqlArgs @("-v", "ON_ERROR_STOP=1", "-c", "SELECT COUNT(*) AS recommendation_event_rows FROM recommendation_event;")

    Write-Host ""
    Write-Host "Initialization completed successfully."
    Write-Host "Target database: $AppDb"
    Write-Host "Sample run:"
    Write-Host "  .\init-db.ps1 -AdminUser postgres -AdminPassword root -AppDb postgres"
}
finally {
    Remove-Item Env:PGPASSWORD -ErrorAction SilentlyContinue
    Remove-Item Env:PGCLIENTENCODING -ErrorAction SilentlyContinue
}
