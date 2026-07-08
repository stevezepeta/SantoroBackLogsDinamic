# Iniciar Backend Principal de Logs
# Este script inicia el servidor en el puerto 8040 con la configuracion principal

param(
    [switch]$Production,
    [switch]$Development
)

Write-Host "================================================================" -ForegroundColor Cyan
Write-Host "  BACKEND PRINCIPAL DE LOGS - SISTEMA DE AUDITORIA" -ForegroundColor Cyan
Write-Host "================================================================" -ForegroundColor Cyan
Write-Host ""

# Configuracion del ambiente
if ($Production) {
    $perfil = "prod"
    $mensaje = "PRODUCCION"
    $color = "Red"
} elseif ($Development) {
    $perfil = "dev"
    $mensaje = "DESARROLLO"
    $color = "Yellow"
} else {
    $perfil = ""
    $mensaje = "DEFECTO"
    $color = "Green"
}

Write-Host "Ambiente: $mensaje" -ForegroundColor $color
Write-Host ""

# Verificar configuracion
Write-Host "Verificando configuracion..." -ForegroundColor Yellow
$verificacion = & .\verificar-config-principal.ps1
if ($LASTEXITCODE -ne 0) {
    Write-Host ""
    Write-Host "ERROR: La configuracion tiene problemas." -ForegroundColor Red
    Write-Host "Ejecuta: .\verificar-config-principal.ps1 -Verbose" -ForegroundColor Yellow
    Write-Host ""
    exit 1
}

Write-Host ""
Write-Host "================================================================" -ForegroundColor Cyan
Write-Host "  CONFIGURACION VERIFICADA - INICIANDO SERVIDOR" -ForegroundColor Cyan
Write-Host "================================================================" -ForegroundColor Cyan
Write-Host ""

Write-Host "Puerto: 8040" -ForegroundColor White
Write-Host "Base de datos: logs_system" -ForegroundColor White
Write-Host "WebSocket: dashboard-api.grupo-santoro.com.mx" -ForegroundColor White
Write-Host ""

Write-Host "Endpoints disponibles:" -ForegroundColor Yellow
Write-Host "  - Swagger UI: http://localhost:8040/swagger-ui.html" -ForegroundColor Cyan
Write-Host "  - API Docs: http://localhost:8040/v3/api-docs" -ForegroundColor Cyan
Write-Host "  - Funnel Analytics: http://localhost:8040/api/analytics/funnel/..." -ForegroundColor Cyan
Write-Host "  - Health: http://localhost:8040/actuator/health" -ForegroundColor Cyan
Write-Host ""

Write-Host "Presiona Ctrl+C para detener el servidor" -ForegroundColor Gray
Write-Host ""
Write-Host "================================================================" -ForegroundColor Cyan
Write-Host ""

# Iniciar el servidor
if ($perfil) {
    Write-Host "Ejecutando: mvnw spring-boot:run -Dspring-boot.run.profiles=$perfil" -ForegroundColor Gray
    Write-Host ""
    & .\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=$perfil"
} else {
    Write-Host "Ejecutando: mvnw spring-boot:run" -ForegroundColor Gray
    Write-Host ""
    & .\mvnw.cmd spring-boot:run
}

