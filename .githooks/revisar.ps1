# Revision automatica con OpenCode. Se ejecuta desde el hook pre-push.
$ErrorActionPreference = 'Continue'
$root = git rev-parse --show-toplevel
Set-Location $root

$prompt = @'
Usa el subagente revisor para revisar todos los Pull Requests abiertos de angeld03/fabric-mod.
Para cada PR di que archivos toca, si se solapa con otro PR abierto, si esta al dia con main y si compila.
Da un veredicto: SEGURO, REVISAR o PELIGROSO.
Si algun PR es PELIGROSO, escribe como ultima linea exactamente: BLOQUEAR
Si todos son seguros o solo requieren revision, escribe como ultima linea exactamente: PERMITIR
No modifiques archivos, no hagas commit, push, merge ni revert.
'@

Write-Output '--- Revision automatica (OpenCode / revisor) ---'
$salida = opencode run --standalone --agent build $prompt 2>&1 | Out-String
Write-Output $salida

if ($salida -match 'BLOQUEAR') {
    Write-Output 'REVISION: BLOQUEADO. Arregla el PR antes de subir.'
    exit 1
}
Write-Output 'REVISION: PERMITIR.'
exit 0
