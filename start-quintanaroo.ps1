# ====================================================================
# Script de Inicio del Ambiente Quintana Roo - Puerto 8057
# ====================================================================
# Este script configura todas las variables de entorno necesarias
# para que el ambiente de Quintana Roo sea completamente independiente
# del ambiente principal (puerto 8040)
# ====================================================================

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "Iniciando Ambiente Quintana Roo" -ForegroundColor Cyan
Write-Host "Puerto: 8057" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

# ===== PUERTO DEL SERVIDOR =====
$env:SERVER_PORT = "8057"
Write-Host "[OK] Puerto configurado: $env:SERVER_PORT" -ForegroundColor Green

# ===== BASE DE DATOS MONGODB =====
$env:MONGODB_URI = "mongodb://localhost:27017/logs_quintanaroo"
$env:MULTITENANT_BASE_DATABASE = "logs_quintanaroo"
Write-Host "[OK] Base de datos: logs_quintanaroo" -ForegroundColor Green

# ===== WEBSOCKET =====
# IMPORTANTE: Por defecto se permite todos los orígenes (*)
# En producción, cambiar por el dominio específico, ejemplo:
# $env:WEBSOCKET_ALLOWED_ORIGINS = "https://dashboard-quintanaroo.grupo-santoro.com.mx,https://localhost:3000"
$env:WEBSOCKET_ALLOWED_ORIGINS = "*"
Write-Host "[OK] WebSocket Origins: $env:WEBSOCKET_ALLOWED_ORIGINS" -ForegroundColor Green

# ===== CONFIGURACIÓN DE CORREO =====
$env:MAIL_HOST = "smtp.gmail.com"
$env:MAIL_PORT = "587"
$env:MAIL_USERNAME = "soporte.tecnico@grupo-santoro.com.mx"
$env:MAIL_PASSWORD = "wnnkjroexgypcpss"
$env:MAIL_FROM = "soporte.tecnico@grupo-santoro.com.mx"
$env:MAIL_FROM_NAME = "Backlogs Santoro - Quintana Roo"
Write-Host "[OK] Configuración de correo establecida" -ForegroundColor Green

# ===== PERFIL ACTIVO =====
$env:SPRING_PROFILES_ACTIVE = "prod"
Write-Host "[OK] Perfil activo: $env:SPRING_PROFILES_ACTIVE" -ForegroundColor Green

Write-Host ""
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "Verificando Pre-requisitos" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan

# Verificar que existe el JAR
$jarPath = ".\target\dinamico-0.0.1-SNAPSHOT.jar"
if (Test-Path $jarPath) {
    Write-Host "[OK] JAR encontrado: $jarPath" -ForegroundColor Green
} else {
    Write-Host "[ERROR] No se encontró el JAR en: $jarPath" -ForegroundColor Red
    Write-Host "Por favor, ejecuta 'mvn clean package' primero" -ForegroundColor Yellow
    exit 1
}

# Verificar conectividad con MongoDB
Write-Host ""
Write-Host "Verificando conexión a MongoDB..." -ForegroundColor Yellow
try {
    $mongoTest = Test-NetConnection -ComputerName "localhost" -Port 27017 -WarningAction SilentlyContinue
    if ($mongoTest.TcpTestSucceeded) {
        Write-Host "[OK] MongoDB accesible en localhost:27017" -ForegroundColor Green
    } else {
        Write-Host "[ADVERTENCIA] No se pudo conectar a MongoDB en localhost:27017" -ForegroundColor Yellow
        Write-Host "Asegúrate de que MongoDB esté ejecutándose" -ForegroundColor Yellow
    }
} catch {
    Write-Host "[ADVERTENCIA] No se pudo verificar MongoDB" -ForegroundColor Yellow
}

# Verificar conectividad con el servidor SMTP
Write-Host ""
Write-Host "Verificando conexión al servidor SMTP..." -ForegroundColor Yellow
try {
    $smtpTest = Test-NetConnection -ComputerName "smtp.gmail.com" -Port 587 -WarningAction SilentlyContinue
    if ($smtpTest.TcpTestSucceeded) {
        Write-Host "[OK] Servidor SMTP accesible (smtp.gmail.com:587)" -ForegroundColor Green
    } else {
        Write-Host "[ADVERTENCIA] No se pudo conectar al servidor SMTP" -ForegroundColor Yellow
        Write-Host "Verifica el firewall o configuración de red" -ForegroundColor Yellow
    }
} catch {
    Write-Host "[ADVERTENCIA] No se pudo verificar servidor SMTP" -ForegroundColor Yellow
}

Write-Host ""
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "Iniciando Aplicación" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""
Write-Host "Configuración resumen:" -ForegroundColor White
Write-Host "  - Puerto: 8057" -ForegroundColor White
Write-Host "  - Base de datos: logs_quintanaroo" -ForegroundColor White
Write-Host "  - WebSocket: ws://localhost:8057/ws" -ForegroundColor White
Write-Host "  - Swagger UI: http://localhost:8057/swagger-ui.html" -ForegroundColor White
Write-Host ""
Write-Host "Presiona Ctrl+C para detener el servidor" -ForegroundColor Yellow
Write-Host ""

# Ejecutar la aplicación
java -jar $jarPath

