# ═══════════════════════════════════════════════════════════════════════════
# Script de Prueba: Password Recovery Flow
# ═══════════════════════════════════════════════════════════════════════════
# Uso:
#   .\test-password-reset.ps1
#   .\test-password-reset.ps1 -BaseUrl "https://api-logs.grupo-santoro.com.mx"
# ═══════════════════════════════════════════════════════════════════════════

param(
    [string]$BaseUrl = "http://localhost:8005",
    [string]$TestEmail = "admin@test.com"
)

$ErrorActionPreference = "Continue"

Write-Host "`n═══════════════════════════════════════════════════════════════════════════" -ForegroundColor Cyan
Write-Host "  PASSWORD RECOVERY FLOW TEST" -ForegroundColor Cyan
Write-Host "═══════════════════════════════════════════════════════════════════════════`n" -ForegroundColor Cyan

Write-Host "🌐 Base URL: $BaseUrl" -ForegroundColor Yellow
Write-Host "📧 Test Email: $TestEmail`n" -ForegroundColor Yellow

# ═══════════════════════════════════════════════════════════════════════════
# PASO 1: Solicitar código de recuperación
# ═══════════════════════════════════════════════════════════════════════════

Write-Host "───────────────────────────────────────────────────────────────────────────" -ForegroundColor Gray
Write-Host "📤 PASO 1: Solicitar código de recuperación" -ForegroundColor Green
Write-Host "───────────────────────────────────────────────────────────────────────────`n" -ForegroundColor Gray

$forgotBody = @{
    email = $TestEmail
} | ConvertTo-Json

Write-Host "Request:" -ForegroundColor DarkGray
Write-Host "POST $BaseUrl/api/auth/forgot-password" -ForegroundColor DarkGray
Write-Host $forgotBody -ForegroundColor DarkGray
Write-Host ""

try {
    $response1 = Invoke-WebRequest `
        -Uri "$BaseUrl/api/auth/forgot-password" `
        -Method POST `
        -ContentType "application/json" `
        -Body $forgotBody `
        -UseBasicParsing `
        -ErrorAction Stop

    $result1 = $response1.Content | ConvertFrom-Json

    Write-Host "✅ Response Status: $($response1.StatusCode)" -ForegroundColor Green
    Write-Host "Response Body:" -ForegroundColor DarkGray
    $result1 | ConvertTo-Json -Depth 5 | Write-Host -ForegroundColor White
    Write-Host ""

    if ($result1.success) {
        Write-Host "✅ Email de recuperación enviado (revisa logs o tu inbox)" -ForegroundColor Green
    } else {
        Write-Host "⚠️  La respuesta indica error" -ForegroundColor Yellow
    }
} catch {
    Write-Host "❌ Error en PASO 1:" -ForegroundColor Red
    Write-Host $_.Exception.Message -ForegroundColor Red
    if ($_.Exception.Response) {
        $reader = New-Object System.IO.StreamReader($_.Exception.Response.GetResponseStream())
        $responseBody = $reader.ReadToEnd()
        Write-Host "Response Body:" -ForegroundColor DarkGray
        Write-Host $responseBody -ForegroundColor Red
    }
    exit 1
}

Write-Host "`n"

# ═══════════════════════════════════════════════════════════════════════════
# PASO 2: Confirmar código y restablecer contraseña
# ═══════════════════════════════════════════════════════════════════════════

Write-Host "───────────────────────────────────────────────────────────────────────────" -ForegroundColor Gray
Write-Host "🔐 PASO 2: Restablecer contraseña con código" -ForegroundColor Green
Write-Host "───────────────────────────────────────────────────────────────────────────`n" -ForegroundColor Gray

Write-Host "⏸️  PAUSADO - Por favor:" -ForegroundColor Yellow
Write-Host "   1. Revisa la consola del backend o tu email" -ForegroundColor Yellow
Write-Host "   2. Copia el código de 6 dígitos" -ForegroundColor Yellow
Write-Host "   3. Pégalo aquí cuando esté listo`n" -ForegroundColor Yellow

$code = Read-Host "Ingresa el código de 6 dígitos"

if ($code -match '^\d{6}$') {
    Write-Host "✅ Código válido (6 dígitos)`n" -ForegroundColor Green
} else {
    Write-Host "❌ Código inválido - debe ser exactamente 6 dígitos numéricos" -ForegroundColor Red
    exit 1
}

$newPassword = Read-Host "Ingresa la nueva contraseña (mínimo 8 caracteres)" -AsSecureString
$newPasswordPlain = [Runtime.InteropServices.Marshal]::PtrToStringAuto(
    [Runtime.InteropServices.Marshal]::SecureStringToBSTR($newPassword)
)

if ($newPasswordPlain.Length -lt 8) {
    Write-Host "❌ Contraseña muy corta - mínimo 8 caracteres" -ForegroundColor Red
    exit 1
}

$resetBody = @{
    email = $TestEmail
    code = $code
    newPassword = $newPasswordPlain
} | ConvertTo-Json

Write-Host "`nRequest:" -ForegroundColor DarkGray
Write-Host "POST $BaseUrl/api/auth/reset-password" -ForegroundColor DarkGray
Write-Host $resetBody -ForegroundColor DarkGray
Write-Host ""

try {
    $response2 = Invoke-WebRequest `
        -Uri "$BaseUrl/api/auth/reset-password" `
        -Method POST `
        -ContentType "application/json" `
        -Body $resetBody `
        -UseBasicParsing `
        -ErrorAction Stop

    $result2 = $response2.Content | ConvertFrom-Json

    Write-Host "✅ Response Status: $($response2.StatusCode)" -ForegroundColor Green
    Write-Host "Response Body:" -ForegroundColor DarkGray
    $result2 | ConvertTo-Json -Depth 5 | Write-Host -ForegroundColor White
    Write-Host ""

    if ($result2.success) {
        Write-Host "✅ ¡CONTRASEÑA ACTUALIZADA EXITOSAMENTE!" -ForegroundColor Green
        Write-Host "   Ya puedes iniciar sesión con tu nueva contraseña`n" -ForegroundColor Green
    } else {
        Write-Host "⚠️  La respuesta indica error" -ForegroundColor Yellow
    }
} catch {
    Write-Host "❌ Error en PASO 2:" -ForegroundColor Red
    Write-Host $_.Exception.Message -ForegroundColor Red
    if ($_.Exception.Response) {
        $reader = New-Object System.IO.StreamReader($_.Exception.Response.GetResponseStream())
        $responseBody = $reader.ReadToEnd()
        Write-Host "Response Body:" -ForegroundColor DarkGray
        Write-Host $responseBody -ForegroundColor Red
    }
    exit 1
}

# ═══════════════════════════════════════════════════════════════════════════
# RESUMEN FINAL
# ═══════════════════════════════════════════════════════════════════════════

Write-Host "`n═══════════════════════════════════════════════════════════════════════════" -ForegroundColor Cyan
Write-Host "  ✅ FLUJO COMPLETADO EXITOSAMENTE" -ForegroundColor Cyan
Write-Host "═══════════════════════════════════════════════════════════════════════════`n" -ForegroundColor Cyan

Write-Host "Próximos pasos:" -ForegroundColor Yellow
Write-Host "  1. Intenta iniciar sesión con tu nueva contraseña" -ForegroundColor White
Write-Host "  2. El código usado ya no es válido (uso único)" -ForegroundColor White
Write-Host "  3. Si olvidas tu password nuevamente, repite el flujo`n" -ForegroundColor White

Write-Host "═══════════════════════════════════════════════════════════════════════════`n" -ForegroundColor Cyan

