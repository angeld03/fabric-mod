# Lanzador SIN GIT: descarga el proyecto y (si hace falta) JDK 25, y ejecuta runClient.
# Solo necesita PowerShell (incluido en Windows) e internet.
$ErrorActionPreference = 'Stop'

$zipUrl = 'https://github.com/angeld03/fabric-mod/archive/refs/heads/angeld03/motor-rpg.zip'
$jdkUrl = 'https://api.adoptium.net/v3/binary/latest/25/ga/windows/x64/jdk/hotspot/normal/eclipse'

$root    = $PSScriptRoot
$zip     = Join-Path $root '.wildbound-latest.zip'
$tmp     = Join-Path $root '.wildbound-extract'
$project = Join-Path $root 'wildbound-juego'
$jdkZip  = Join-Path $root '.jdk25.zip'
$jdkTmp  = Join-Path $root '.jdk25tmp'
$jdkRoot = Join-Path $root '.jdk25'

function Test-Jdk25($javaExe) {
    if (-not (Test-Path $javaExe)) { return $false }
    $v = (& $javaExe -version 2>&1 | Out-String)
    return ($v -match '"25')
}

# --- Java 25 ---------------------------------------------------------------
$jdkHome = $null
$cmd = Get-Command java -ErrorAction SilentlyContinue
if ($cmd -and (Test-Jdk25 $cmd.Source)) {
    $jdkHome = Split-Path (Split-Path $cmd.Source -Parent) -Parent
    Write-Output "Java 25 encontrado: $jdkHome"
}
if (-not $jdkHome -and (Test-Jdk25 (Join-Path $jdkRoot 'bin\java.exe'))) {
    $jdkHome = $jdkRoot
    Write-Output "Java 25 local encontrado: $jdkHome"
}
if (-not $jdkHome) {
    Write-Output 'Descargando JDK 25 (solo la primera vez, ~140 MB)...'
    Invoke-WebRequest -Uri $jdkUrl -OutFile $jdkZip -UseBasicParsing
    Remove-Item $jdkTmp -Recurse -Force -ErrorAction SilentlyContinue
    Expand-Archive -Path $jdkZip -DestinationPath $jdkTmp -Force
    $found = Get-ChildItem $jdkTmp -Directory | Select-Object -First 1
    if (-not $found) { throw 'No se pudo extraer el JDK' }
    Remove-Item $jdkRoot -Recurse -Force -ErrorAction SilentlyContinue
    Move-Item $found.FullName $jdkRoot -Force
    Remove-Item $jdkTmp -Recurse -Force -ErrorAction SilentlyContinue
    $jdkHome = $jdkRoot
    Write-Output "JDK 25 instalado en: $jdkHome"
}

# --- Proyecto ---------------------------------------------------------------
Write-Output 'Descargando la ultima version del motor desde GitHub...'
Invoke-WebRequest -Uri $zipUrl -OutFile $zip -UseBasicParsing
Remove-Item $tmp -Recurse -Force -ErrorAction SilentlyContinue
Expand-Archive -Path $zip -DestinationPath $tmp -Force
$inside = Get-ChildItem $tmp -Directory | Select-Object -First 1
if (-not $inside) { throw 'No se pudo extraer el proyecto' }
Remove-Item $project -Recurse -Force -ErrorAction SilentlyContinue
Move-Item $inside.FullName $project -Force
Write-Output "Proyecto actualizado en: $project"

# --- Arrancar ---------------------------------------------------------------
$env:JAVA_HOME = $jdkHome
$env:PATH = "$jdkHome\bin;$env:PATH"
Push-Location $project
try {
    & .\gradlew.bat runClient
} finally {
    Pop-Location
}
exit $LASTEXITCODE
