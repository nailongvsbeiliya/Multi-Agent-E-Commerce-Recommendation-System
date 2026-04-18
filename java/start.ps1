param(
    [switch]$Llm,
    [string]$JavaHome,
    [switch]$SkipDbCheck
)

$ErrorActionPreference = "Stop"

function Test-JdkHome {
    param([string]$PathToCheck)
    if ([string]::IsNullOrWhiteSpace($PathToCheck)) {
        return $false
    }
    $javaExe = Join-Path $PathToCheck "bin\java.exe"
    $javacExe = Join-Path $PathToCheck "bin\javac.exe"
    return (Test-Path $javaExe) -and (Test-Path $javacExe)
}

function Resolve-JavaHome {
    param([string]$PreferredJavaHome)

    if (Test-JdkHome $PreferredJavaHome) {
        return $PreferredJavaHome
    }

    if (Test-JdkHome $env:JAVA_HOME) {
        return $env:JAVA_HOME
    }

    $javaCmd = (Get-Command java -ErrorAction Stop).Source
    $binDir = Split-Path $javaCmd -Parent
    $javaHomeLocal = Split-Path $binDir -Parent
    if ($javaHomeLocal -match "^[A-Za-z]:\\?$") {
        throw "Detected invalid JAVA_HOME candidate: '$javaHomeLocal'. Please pass -JavaHome explicitly."
    }
    if (-not (Test-JdkHome $javaHomeLocal)) {
        throw "Detected java at '$javaCmd' but cannot find full JDK (bin\\javac.exe). Please pass -JavaHome explicitly."
    }
    return $javaHomeLocal
}

function Test-Postgres {
    param(
        [string]$DbHost = "localhost",
        [int]$Port = 5432
    )
    $probe = Test-NetConnection -ComputerName $DbHost -Port $Port -WarningAction SilentlyContinue
    return [bool]$probe.TcpTestSucceeded
}

$javaHomeResolved = Resolve-JavaHome -PreferredJavaHome $JavaHome
$env:JAVA_HOME = $javaHomeResolved
$env:Path = (Join-Path $javaHomeResolved "bin") + ";" + $env:Path
Write-Host "[startup] JAVA_HOME = $javaHomeResolved"

$dbHost = if ($env:DB_HOST) { $env:DB_HOST } else { "localhost" }
$dbPort = if ($env:DB_PORT) { [int]$env:DB_PORT } else { 5432 }

if (-not $SkipDbCheck) {
    if (-not (Test-Postgres -DbHost $dbHost -Port $dbPort)) {
        Write-Host "[error] PostgreSQL is not reachable at $dbHost`:$dbPort"
        Write-Host "[hint] Start PostgreSQL first, or update DB_HOST/DB_PORT/DB_NAME/DB_USER/DB_PASSWORD."
        exit 1
    }
}

$workdir = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $workdir

$args = @(
    "-Dmaven.compiler.release=17",
    "-Dmaven.compiler.source=17",
    "-Dmaven.compiler.target=17",
    "-Dmaven.test.skip=true",
    "spring-boot:run"
)
if ($Llm) {
    $args = @("-Dspring-boot.run.profiles=llm") + $args
}

Write-Host "[startup] running: mvn $($args -join ' ')"
& mvn @args
