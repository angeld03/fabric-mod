# Un solo comando: actualiza el motor y lanza Minecraft con el mod.
# Uso:  .\jugar.ps1
$ErrorActionPreference = 'Continue'
Set-Location $PSScriptRoot

$rama = 'angeld03/motor-rpg'

Write-Output '--- Actualizando cambios ---'
git fetch origin
$actual = git branch --show-current
if ($actual -ne $rama) {
    git switch $rama
    if ($LASTEXITCODE -ne 0) {
        Write-Output "No se pudo cambiar a la rama $rama. Guarda o descarta tus cambios y prueba otra vez."
        exit 1
    }
}
git pull --ff-only origin $rama
if ($LASTEXITCODE -ne 0) {
    Write-Output 'No se pudo actualizar. Revisa tus cambios locales.'
    exit 1
}

Write-Output '--- Comprobando Java ---'
$java = java -version 2>&1 | Select-Object -First 1
Write-Output $java
if ($java -notmatch '25') {
    Write-Output 'AVISO: Fabric 26.3 necesita JDK 25. Instalalo si el juego no arranca.'
}

Write-Output '--- Arrancando Minecraft (runClient) ---'
.\gradlew runClient
exit $LASTEXITCODE
