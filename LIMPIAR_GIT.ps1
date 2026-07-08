# Script para limpiar archivos sensibles del repositorio Git
# IMPORTANTE: Este script elimina los archivos del índice de git pero los mantiene en el disco

Write-Host "=== Limpieza de Archivos Sensibles del Repositorio ===" -ForegroundColor Cyan
Write-Host ""

# Cambiar al directorio del proyecto
Set-Location "C:\WorkSpace\Santoro\BackLogs\SantoroBackLogsDinamic"

# Eliminar la carpeta data/ del índice de git (pero mantenerla en disco)
Write-Host "1. Eliminando carpeta data/ del índice de git..." -ForegroundColor Yellow
git rm -r --cached data/

# Verificar el estado
Write-Host ""
Write-Host "2. Estado actual de git:" -ForegroundColor Yellow
git status --short

Write-Host ""
Write-Host "3. SIGUIENTE PASO:" -ForegroundColor Green
Write-Host "   Ejecuta: git add .gitignore" -ForegroundColor White
Write-Host "   Luego: git commit -m 'chore: actualizar .gitignore y eliminar archivos sensibles'" -ForegroundColor White
Write-Host ""
Write-Host "NOTA: Los archivos siguen en tu disco, solo se eliminarán del repositorio." -ForegroundColor Cyan

