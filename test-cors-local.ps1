# test-cors-local.ps1
# Script para verificar configuración CORS antes de deploy a AWS

Write-Host "═══════════════════════════════════════════════════════" -ForegroundColor Cyan
Write-Host "  Test de Configuración CORS - Backend Logs Santoro" -ForegroundColor Cyan
Write-Host "═══════════════════════════════════════════════════════" -ForegroundColor Cyan
Write-Host ""

$baseUrl = "http://localhost:8005"
$tenantId = "68ed8cdedca3a97d9f999ba9"

# Verificar si el servidor está corriendo
Write-Host "[1/4] Verificando servidor..." -ForegroundColor Yellow
try {
    $healthCheck = Invoke-WebRequest -Uri "$baseUrl/actuator/health" -Method GET -ErrorAction Stop
    Write-Host "✓ Servidor activo en $baseUrl" -ForegroundColor Green
} catch {
    Write-Host "✗ Servidor no responde. Ejecuta: .\mvnw.cmd spring-boot:run" -ForegroundColor Red
    exit 1
}

Write-Host ""
Write-Host "[2/4] Test Preflight OPTIONS..." -ForegroundColor Yellow

# Test OPTIONS (preflight request)
$headers = @{
    "Origin" = "http://localhost:4200"
    "Access-Control-Request-Method" = "POST"
    "Access-Control-Request-Headers" = "X-Tenant-Id,Content-Type,Authorization"
}

try {
    $response = Invoke-WebRequest -Uri "$baseUrl/api/auth/login" `
        -Method OPTIONS `
        -Headers $headers `
        -ErrorAction Stop

    $allowHeaders = $response.Headers["Access-Control-Allow-Headers"]
    $exposeHeaders = $response.Headers["Access-Control-Expose-Headers"]

    Write-Host "✓ OPTIONS returned HTTP $($response.StatusCode)" -ForegroundColor Green

    if ($allowHeaders -match "X-Tenant" -and $allowHeaders -match "X-Tenant-Id") {
        Write-Host "✓ X-Tenant, X-Tenant-Id en AllowedHeaders" -ForegroundColor Green
    } else {
        Write-Host "✗ Headers de tenancy NO encontrados en AllowedHeaders" -ForegroundColor Red
        Write-Host "  Actual: $allowHeaders" -ForegroundColor Gray
    }

    if ($exposeHeaders -match "X-Tenant" -and $exposeHeaders -match "X-Tenant-Id") {
        Write-Host "✓ X-Tenant, X-Tenant-Id en ExposedHeaders" -ForegroundColor Green
    } else {
        Write-Host "✗ Headers de tenancy NO encontrados en ExposedHeaders" -ForegroundColor Red
        Write-Host "  Actual: $exposeHeaders" -ForegroundColor Gray
    }

} catch {
    Write-Host "✗ Error en preflight: $_" -ForegroundColor Red
}

Write-Host ""
Write-Host "[3/4] Test POST con X-Tenant-Id header..." -ForegroundColor Yellow

$loginBody = @{
    email = "admin@grupo-santoro.com.mx"
    password = "password123"  # cambiar por tu password de prueba
} | ConvertTo-Json

$postHeaders = @{
    "Content-Type" = "application/json"
    "X-Tenant-Id" = $tenantId
    "Origin" = "http://localhost:4200"
}

try {
    $response = Invoke-WebRequest -Uri "$baseUrl/api/auth/login" `
        -Method POST `
        -Headers $postHeaders `
        -Body $loginBody `
        -ErrorAction Stop

    Write-Host "✓ POST con X-Tenant-Id accepted (HTTP $($response.StatusCode))" -ForegroundColor Green

} catch {
    $statusCode = $_.Exception.Response.StatusCode.value__
    if ($statusCode -eq 401 -or $statusCode -eq 400) {
        Write-Host "✓ POST aceptado (credenciales incorrectas es esperado)" -ForegroundColor Green
    } elseif ($statusCode -eq 403) {
        Write-Host "✗ 403 Forbidden - CORS no está funcionando correctamente" -ForegroundColor Red
    } else {
        Write-Host "⚠ HTTP $statusCode - Revisar logs del servidor" -ForegroundColor Yellow
    }
}

Write-Host ""
Write-Host "[4/4] Test endpoint público validate-external..." -ForegroundColor Yellow

$validateBody = @{
    email = "admin@grupo-santoro.com.mx"
    password = "test123"
} | ConvertTo-Json

$validateHeaders = @{
    "Content-Type" = "application/json"
}

try {
    $response = Invoke-WebRequest -Uri "$baseUrl/api/auth/validate-external" `
        -Method POST `
        -Headers $validateHeaders `
        -Body $validateBody `
        -ErrorAction Stop

    Write-Host "✓ validate-external accessible sin auth (HTTP $($response.StatusCode))" -ForegroundColor Green

    $json = $response.Content | ConvertFrom-Json
    if ($json.data -ne $null) {
        Write-Host "  Response: isValid=$($json.data.isValid)" -ForegroundColor Gray
    }

} catch {
    $statusCode = $_.Exception.Response.StatusCode.value__
    Write-Host "✗ validate-external falló con HTTP $statusCode" -ForegroundColor Red
}

Write-Host ""
Write-Host "═══════════════════════════════════════════════════════" -ForegroundColor Cyan
Write-Host "  Test completado" -ForegroundColor Cyan
Write-Host "═══════════════════════════════════════════════════════" -ForegroundColor Cyan
Write-Host ""
Write-Host "Siguiente paso: Deploy a AWS y ejecutar el mismo test contra:" -ForegroundColor White
Write-Host "  https://api-logs.grupo-santoro.com.mx" -ForegroundColor Cyan
Write-Host ""

