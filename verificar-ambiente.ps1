# ====================================================================
# Script de Verificacion - Ambiente Quintana Roo
# ====================================================================
# Este script verifica que todos los pre-requisitos esten cumplidos
# antes de desplegar el ambiente de Quintana Roo en el puerto 8057
# ====================================================================

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "VERIFICACION DE PRE-REQUISITOS" -ForegroundColor Cyan
Write-Host "Ambiente Quintana Roo - Puerto 8057" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

$errores = 0
$advertencias = 0

# ===== 1. VERIFICAR JAR =====
Write-Host "[1/7] Verificando archivo JAR..." -ForegroundColor Yellow
$jarPath = ".\target\dinamico-0.0.1-SNAPSHOT.jar"
if (Test-Path $jarPath) {
    $jarSize = (Get-Item $jarPath).Length / 1MB
    if ($jarSize -gt 50) {
        Write-Host "  [OK] JAR encontrado: $jarPath (${jarSize} MB)" -ForegroundColor Green
    } else {
        Write-Host "  [ADVERTENCIA] JAR parece incompleto (${jarSize} MB)" -ForegroundColor Yellow
        $advertencias++
    }
} else {
    Write-Host "  [ERROR] JAR no encontrado. Ejecuta 'mvn clean package'" -ForegroundColor Red
    $errores++
}

# ===== 2. VERIFICAR MONGODB =====
Write-Host ""
Write-Host "[2/7] Verificando MongoDB..." -ForegroundColor Yellow
try {
    $mongoTest = Test-NetConnection -ComputerName "localhost" -Port 27017 -WarningAction SilentlyContinue -InformationLevel Quiet
    if ($mongoTest.TcpTestSucceeded) {
        Write-Host "  [OK] MongoDB accesible en localhost:27017" -ForegroundColor Green
    } else {
        Write-Host "  [ERROR] MongoDB no accesible en localhost:27017" -ForegroundColor Red
        $errores++
    }
} catch {
    Write-Host "  [ERROR] No se pudo verificar MongoDB" -ForegroundColor Red
    $errores++
}

# ===== 3. VERIFICAR PUERTO 8057 =====
Write-Host ""
Write-Host "[3/7] Verificando puerto 8057..." -ForegroundColor Yellow
$portInUse = Get-NetTCPConnection -LocalPort 8057 -ErrorAction SilentlyContinue
if ($portInUse) {
    Write-Host "  [ADVERTENCIA] Puerto 8057 ya esta en uso" -ForegroundColor Yellow
    Write-Host "  Proceso: $($portInUse.OwningProcess)" -ForegroundColor Yellow
    $advertencias++
} else {
    Write-Host "  [OK] Puerto 8057 disponible" -ForegroundColor Green
}

# ===== 4. VERIFICAR SMTP =====
Write-Host ""
Write-Host "[4/7] Verificando conectividad SMTP..." -ForegroundColor Yellow
try {
    $smtpTest = Test-NetConnection -ComputerName "smtp.gmail.com" -Port 587 -WarningAction SilentlyContinue -InformationLevel Quiet
    if ($smtpTest.TcpTestSucceeded) {
        Write-Host "  [OK] Servidor SMTP accesible (smtp.gmail.com:587)" -ForegroundColor Green
    } else {
        Write-Host "  [ADVERTENCIA] No se puede conectar a SMTP. Los correos pueden fallar." -ForegroundColor Yellow
        $advertencias++
    }
} catch {
    Write-Host "  [ADVERTENCIA] No se pudo verificar SMTP" -ForegroundColor Yellow
    $advertencias++
}

# ===== 5. VERIFICAR ARCHIVOS DE CONFIGURACION =====
Write-Host ""
Write-Host "[5/7] Verificando archivos de configuracion..." -ForegroundColor Yellow
$configFiles = @(
    ".\src\main\resources\application.properties",
    ".\src\main\resources\application.yml",
    ".\src\main\resources\application-dev.properties",
    ".\src\main\resources\application-dev.yml",
    ".\src\main\java\backlogs\dinamico\infra\ws\WebSocketConfig.java"
)

$allConfigsOk = $true
foreach ($file in $configFiles) {
    if (Test-Path $file) {
        Write-Host "  [OK] $file" -ForegroundColor Green
    } else {
        Write-Host "  [ERROR] No encontrado: $file" -ForegroundColor Red
        $errores++
        $allConfigsOk = $false
    }
}

# ===== 6. VERIFICAR CONTENIDO DE CONFIGURACION =====
Write-Host ""
Write-Host "[6/7] Verificando configuracion de base de datos..." -ForegroundColor Yellow

$appYml = Get-Content ".\src\main\resources\application.yml" -Raw -ErrorAction SilentlyContinue
if ($appYml -match "logs_quintanaroo") {
    Write-Host "  [OK] Base de datos configurada como 'logs_quintanaroo'" -ForegroundColor Green
} else {
    Write-Host "  [ADVERTENCIA] La base de datos podria no estar configurada correctamente" -ForegroundColor Yellow
    $advertencias++
}

$wsConfig = Get-Content ".\src\main\java\backlogs\dinamico\infra\ws\WebSocketConfig.java" -Raw -ErrorAction SilentlyContinue
if ($wsConfig -match "app\.websocket\.allowed-origins") {
    Write-Host "  [OK] WebSocket configurado dinamicamente" -ForegroundColor Green
} else {
    Write-Host "  [ADVERTENCIA] WebSocket podria tener configuracion fija" -ForegroundColor Yellow
    $advertencias++
}

# ===== 7. VERIFICAR SCRIPTS DE INICIO =====
Write-Host ""
Write-Host "[7/7] Verificando scripts de inicio..." -ForegroundColor Yellow
if (Test-Path ".\start-quintanaroo.ps1") {
    Write-Host "  [OK] Script de inicio encontrado: start-quintanaroo.ps1" -ForegroundColor Green
} else {
    Write-Host "  [ADVERTENCIA] Script start-quintanaroo.ps1 no encontrado" -ForegroundColor Yellow
    $advertencias++
}

# ===== RESUMEN =====
Write-Host ""
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "RESUMEN DE VERIFICACION" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

if ($errores -eq 0 -and $advertencias -eq 0) {
    Write-Host "✅ TODAS LAS VERIFICACIONES PASARON" -ForegroundColor Green
    Write-Host ""
    Write-Host "El ambiente esta listo para desplegar." -ForegroundColor Green
    Write-Host "Ejecuta: .\start-quintanaroo.ps1" -ForegroundColor Cyan
} elseif ($errores -eq 0) {
    Write-Host "⚠️  VERIFICACION COMPLETADA CON ADVERTENCIAS" -ForegroundColor Yellow
    Write-Host ""
    Write-Host "Errores criticos: $errores" -ForegroundColor Green
    Write-Host "Advertencias: $advertencias" -ForegroundColor Yellow
    Write-Host ""
    Write-Host "Puedes desplegar, pero revisa las advertencias." -ForegroundColor Yellow
} else {
    Write-Host "❌ VERIFICACION FALLIDA" -ForegroundColor Red
    Write-Host ""
    Write-Host "Errores criticos: $errores" -ForegroundColor Red
    Write-Host "Advertencias: $advertencias" -ForegroundColor Yellow
    Write-Host ""
    Write-Host "Corrige los errores antes de desplegar." -ForegroundColor Red
}

Write-Host ""
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

# Pausar para que el usuario pueda leer los resultados
if ($errores -gt 0) {
    Read-Host "Presiona Enter para salir"
}

