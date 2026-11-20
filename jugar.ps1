# Un solo comando: actualiza el motor y lanza Minecraft con el mod.
# Uso:  .\jugar.ps1   (o doble clic en jugar.cmd)
$ErrorActionPreference = 'Continue'
Set-Location $PSScriptRoot

function Find-Git {
    if (Get-Command git -ErrorAction SilentlyContinue) { return }
    $candidates = @(
        "$env:ProgramFiles\Git\cmd\git.exe",
        "${env:ProgramFiles(x86)}\Git\cmd\git.exe",
        "$env:LOCALAPPDATA\Programs\Git\cmd\git.exe",
        "$env:ProgramFiles\Git\bin\git.exe"
    )
    foreach ($c in $candidates) {
        if (Test-Path $c) {
            $env:PATH = (Split-Path $c) + ';' + $env:PATH
            return
        }
    }
}

Find-Git
if (-not (Get-Command git -ErrorAction SilentlyContinue)) {
    Write-Output 'ERROR: Git no esta instalado o no se encuentra en el PATH.'
    Write-Output 'Solucion: instala Git for Windows desde https://git-scm.com/download/win'
    Write-Output 'y REINICIA PowerShell despues de instalarlo.'
    Write-Output 'Alternativa: instala GitHub Desktop (incluye Git) e inicia sesion.'
    exit 1
}

$rama = 'angeld03/motor-rpg'

Write-Output '--- Actualizando cambios ---'
git fetch origin
$actual = git branch --show-current
if ($actual -ne $rama) {
    git switch $rama
    if ($LASTEXITCODE -ne 0) {
        Write-Output "No se pudo cambiar a la rama $rama. Guarda tus cambios con: git stash"
        exit 1
    }
}
git pull --ff-only origin $rama
if ($LASTEXITCODE -ne 0) {
    Write-Output 'No se pudo actualizar. Ejecuta: git stash  y vuelve a intentarlo.'
    exit 1
}

Write-Output '--- Comprobando Java ---'
if (-not (Get-Command java -ErrorAction SilentlyContinue)) {
    Write-Output 'ERROR: Java no esta en el PATH. Instala JDK 25 (Temurin o Microsoft).'
    exit 1
}
$java = java -version 2>&1 | Select-Object -First 1
Write-Output $java
if ($java -notmatch '25') {
    Write-Output 'AVISO: Fabric 26.3 necesita JDK 25. Instalalo si el juego no arranca.'
}

Write-Output '--- Arrancando Minecraft (runClient) ---'
.\gradlew runClient
exit $LASTEXITCODE
