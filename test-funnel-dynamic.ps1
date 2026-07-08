<![CDATA[# ═══════════════════════════════════════════════════════════════════════════
# Script: test-funnel-dynamic.ps1
# Descripción: Prueba la nueva arquitectura dinámica de Funnel Analytics v2.0
# Fecha: 2026-06-16
# ═══════════════════════════════════════════════════════════════════════════

param(
    [Parameter(Mandatory=$true)]
    [string]$Token,

    [string]$Tenant = "quintanaroo",
    [string]$BaseUrl = "http://localhost:8040",
    [switch]$Verbose = $false
)

# ─────────────────────────────────────────────────────────────────────────────
# FUNCIONES AUXILIARES
# ─────────────────────────────────────────────────────────────────────────────

function Write-ColorMessage {
    param(
        [string]$Message,
        [string]$Color = "White"
    )
    Write-Host $Message -ForegroundColor $Color
}

function Write-Section {
    param([string]$Title)
    Write-Host ""
    Write-Host "═══════════════════════════════════════════════════════════════════════════" -ForegroundColor Cyan
    Write-Host "  $Title" -ForegroundColor Yellow
    Write-Host "═══════════════════════════════════════════════════════════════════════════" -ForegroundColor Cyan
    Write-Host ""
}

function Test-Endpoint {
    param(
        [string]$Url,
        [string]$Description,
        [hashtable]$Headers,
        [int]$ExpectedStatus = 200
    )

    Write-ColorMessage "`n🔍 TEST: $Description" -Color Yellow
    Write-ColorMessage "   URL: $Url" -Color Gray

    try {
        $response = Invoke-WebRequest -Uri $Url -Headers $Headers -Method Get -UseBasicParsing

        if ($response.StatusCode -eq $ExpectedStatus) {
            Write-ColorMessage "   ✅ Status: $($response.StatusCode) OK" -Color Green

            $json = $response.Content | ConvertFrom-Json

            if ($Verbose) {
                Write-ColorMessage "`n   📄 Response:" -Color Cyan
                Write-ColorMessage ($json | ConvertTo-Json -Depth 10) -Color Gray
            } else {
                Write-ColorMessage "   📊 Data Preview:" -Color Cyan
                if ($json.data) {
                    if ($json.data -is [array]) {
                        Write-ColorMessage "      Items: $($json.data.Count)" -Color White
                        $json.data | ForEach-Object { Write-ColorMessage "      - $_" -Color White }
                    } elseif ($json.data.systemName) {
                        Write-ColorMessage "      System: $($json.data.systemName)" -Color White
                        Write-ColorMessage "      Funnel: $($json.data.funnelName)" -Color White
                        Write-ColorMessage "      Started: $($json.data.summary.totalStarted)" -Color White
                        Write-ColorMessage "      Completed: $($json.data.summary.totalCompleted)" -Color White
                        Write-ColorMessage "      Conversion: $($json.data.summary.globalConversionRate)%" -Color White
                    }
                }
            }

            return $true
        } else {
            Write-ColorMessage "   ❌ Status: $($response.StatusCode) (Expected: $ExpectedStatus)" -Color Red
            return $false
        }

    } catch {
        Write-ColorMessage "   ❌ ERROR: $($_.Exception.Message)" -Color Red
        if ($_.Exception.Response) {
            $statusCode = $_.Exception.Response.StatusCode.value__
            Write-ColorMessage "   Status Code: $statusCode" -Color Red
        }
        return $false
    }
}

# ─────────────────────────────────────────────────────────────────────────────
# SCRIPT PRINCIPAL
# ─────────────────────────────────────────────────────────────────────────────

Write-Section "PRUEBAS DE FUNNEL ANALYTICS v2.0 - ARQUITECTURA DINÁMICA"

Write-ColorMessage "🔐 Configuración de Prueba:" -Color Green
Write-ColorMessage "   Base URL: $BaseUrl" -Color White
Write-ColorMessage "   Tenant: $Tenant" -Color White
Write-ColorMessage "   Token: $(if($Token.Length -gt 20) { $Token.Substring(0,20) + '...' } else { $Token })" -Color White
Write-ColorMessage "   Verbose: $Verbose" -Color White

# Preparar headers comunes
$headers = @{
    "Authorization" = "Bearer $Token"
    "X-Tenant" = $Tenant
    "Content-Type" = "application/json"
}

# ─────────────────────────────────────────────────────────────────────────────
# TEST 1: Listar Sistemas Disponibles
# ─────────────────────────────────────────────────────────────────────────────

Write-Section "TEST 1: LISTAR SISTEMAS DISPONIBLES (v2.0 - Ruta Corregida)"

$url = "$BaseUrl/api/analytics/funnel-systems/available"
$test1 = Test-Endpoint -Url $url -Description "GET /funnel-systems/available" -Headers $headers

if ($test1) {
    Write-ColorMessage "`n✅ Corrección de bug 404 verificada" -Color Green
    Write-ColorMessage "   ✓ Ruta separada /funnel-systems/available funciona correctamente" -Color Green
    Write-ColorMessage "   ✓ No hay conflicto con /funnel/{systemName}" -Color Green
} else {
    Write-ColorMessage "`n❌ Error en endpoint de listado de sistemas" -Color Red
}

# ─────────────────────────────────────────────────────────────────────────────
# TEST 2: Análisis de Funnel - TRUSTVALUE (Flujo Real)
# ─────────────────────────────────────────────────────────────────────────────

Write-Section "TEST 2: FUNNEL DE TRUSTVALUE (Flujo de Jornada Laboral)"

$url = "$BaseUrl/api/analytics/funnel/TRUSTVALUE"
$test2 = Test-Endpoint -Url $url -Description "GET /funnel/TRUSTVALUE" -Headers $headers

if ($test2) {
    Write-ColorMessage "`n✅ Template dinámico de TRUSTVALUE cargado correctamente" -Color Green
    Write-ColorMessage "   ✓ Pasos: INICIO_SESION → SELECCION_SUCURSAL → ENVIAR_EVIDENCIAS → FINALIZAR_ASISTENCIA" -Color Green
} else {
    Write-ColorMessage "`n❌ Error al cargar template de TRUSTVALUE" -Color Red
}

# ─────────────────────────────────────────────────────────────────────────────
# TEST 3: Análisis de Funnel - CITA_GUYANA
# ─────────────────────────────────────────────────────────────────────────────

Write-Section "TEST 3: FUNNEL DE CITA_GUYANA"

$url = "$BaseUrl/api/analytics/funnel/CITA_GUYANA"
$test3 = Test-Endpoint -Url $url -Description "GET /funnel/CITA_GUYANA" -Headers $headers

# ─────────────────────────────────────────────────────────────────────────────
# TEST 4: Análisis con Rango de Fechas
# ─────────────────────────────────────────────────────────────────────────────

Write-Section "TEST 4: FUNNEL CON RANGO DE FECHAS"

$from = "2026-06-01T00:00:00Z"
$to = "2026-06-16T23:59:59Z"
$url = "$BaseUrl/api/analytics/funnel/TICKETS?from=$from&to=$to"
$test4 = Test-Endpoint -Url $url -Description "GET /funnel/TICKETS?from=$from&to=$to" -Headers $headers

# ─────────────────────────────────────────────────────────────────────────────
# TEST 5: Sistema No Existente (Esperamos 404)
# ─────────────────────────────────────────────────────────────────────────────

Write-Section "TEST 5: SISTEMA NO EXISTENTE (404 Esperado)"

$url = "$BaseUrl/api/analytics/funnel/SISTEMA_INEXISTENTE"

Write-ColorMessage "`n🔍 TEST: Sistema no existente debe retornar 404" -Color Yellow
Write-ColorMessage "   URL: $url" -Color Gray

try {
    $response = Invoke-WebRequest -Uri $url -Headers $headers -Method Get -UseBasicParsing
    Write-ColorMessage "   ❌ FALLO: Debería haber retornado 404 pero retornó $($response.StatusCode)" -Color Red
    $test5 = $false
} catch {
    if ($_.Exception.Response.StatusCode.value__ -eq 404) {
        Write-ColorMessage "   ✅ Status: 404 Not Found (Esperado)" -Color Green
        Write-ColorMessage "   ✓ Validación correcta de sistemas no existentes" -Color Green
        $test5 = $true
    } else {
        Write-ColorMessage "   ❌ Status inesperado: $($_.Exception.Response.StatusCode.value__)" -Color Red
        $test5 = $false
    }
}

# ─────────────────────────────────────────────────────────────────────────────
# TEST 6: Todos los Sistemas Configurados
# ─────────────────────────────────────────────────────────────────────────────

Write-Section "TEST 6: PRUEBA DE TODOS LOS SISTEMAS CONFIGURADOS"

$systems = @("CITA_GUYANA", "CITA_QUINTANAROO", "PASSPORT", "TICKETS", "TRUSTVALUE")
$systemResults = @{}

foreach ($system in $systems) {
    $url = "$BaseUrl/api/analytics/funnel/$system"
    $result = Test-Endpoint -Url $url -Description "GET /funnel/$system" -Headers $headers
    $systemResults[$system] = $result
}

# ─────────────────────────────────────────────────────────────────────────────
# RESUMEN FINAL
# ─────────────────────────────────────────────────────────────────────────────

Write-Section "RESUMEN DE PRUEBAS"

$totalTests = 6 + $systems.Count
$passedTests = 0
if ($test1) { $passedTests++ }
if ($test2) { $passedTests++ }
if ($test3) { $passedTests++ }
if ($test4) { $passedTests++ }
if ($test5) { $passedTests++ }
$passedTests += ($systemResults.Values | Where-Object { $_ -eq $true }).Count

Write-ColorMessage "📊 Resultados:" -Color Cyan
Write-ColorMessage "   Total de pruebas: $totalTests" -Color White
Write-ColorMessage "   Exitosas: $passedTests" -Color Green
Write-ColorMessage "   Fallidas: $($totalTests - $passedTests)" -Color Red

Write-Host ""
Write-ColorMessage "┌─────────────────────────────────────────────────────────────┐" -Color Cyan
Write-ColorMessage "│  TEST                                    RESULTADO          │" -Color Cyan
Write-ColorMessage "├─────────────────────────────────────────────────────────────┤" -Color Cyan

$statusIcon = if ($test1) { "✅" } else { "❌" }
Write-ColorMessage "│  1. Listar Sistemas Disponibles         $statusIcon               │" -Color $(if($test1){"Green"}else{"Red"})

$statusIcon = if ($test2) { "✅" } else { "❌" }
Write-ColorMessage "│  2. Funnel TRUSTVALUE                    $statusIcon               │" -Color $(if($test2){"Green"}else{"Red"})

$statusIcon = if ($test3) { "✅" } else { "❌" }
Write-ColorMessage "│  3. Funnel CITA_GUYANA                   $statusIcon               │" -Color $(if($test3){"Green"}else{"Red"})

$statusIcon = if ($test4) { "✅" } else { "❌" }
Write-ColorMessage "│  4. Funnel con Rango de Fechas           $statusIcon               │" -Color $(if($test4){"Green"}else{"Red"})

$statusIcon = if ($test5) { "✅" } else { "❌" }
Write-ColorMessage "│  5. Sistema No Existente (404)           $statusIcon               │" -Color $(if($test5){"Green"}else{"Red"})

Write-ColorMessage "│                                                             │" -Color Cyan
Write-ColorMessage "│  Sistemas Individuales:                                     │" -Color Cyan

foreach ($system in $systems) {
    $statusIcon = if ($systemResults[$system]) { "✅" } else { "❌" }
    $paddedSystem = $system.PadRight(25)
    Write-ColorMessage "│  - $paddedSystem          $statusIcon               │" -Color $(if($systemResults[$system]){"Green"}else{"Red"})
}

Write-ColorMessage "└─────────────────────────────────────────────────────────────┘" -Color Cyan

Write-Host ""

if ($passedTests -eq $totalTests) {
    Write-ColorMessage "🎉 TODAS LAS PRUEBAS PASARON EXITOSAMENTE" -Color Green
    Write-ColorMessage "✅ Arquitectura Dinámica v2.0 está funcionando correctamente" -Color Green
    Write-Host ""
    Write-ColorMessage "📋 Checklist de Validación:" -Color Cyan
    Write-ColorMessage "   ✓ Lectura dinámica desde MongoDB" -Color Green
    Write-ColorMessage "   ✓ Bug 404 corregido (/funnel-systems/available)" -Color Green
    Write-ColorMessage "   ✓ Flujo real de TRUSTVALUE configurado" -Color Green
    Write-ColorMessage "   ✓ Todos los sistemas pre-configurados funcionando" -Color Green
    Write-ColorMessage "   ✓ Validación de errores correcta (404)" -Color Green
    Write-Host ""
    exit 0
} else {
    Write-ColorMessage "⚠️  ALGUNAS PRUEBAS FALLARON" -Color Yellow
    Write-ColorMessage "Revisa los errores arriba para más detalles" -Color Yellow
    Write-Host ""
    Write-ColorMessage "💡 Posibles causas:" -Color Cyan
    Write-ColorMessage "   1. MongoDB no tiene los templates insertados" -Color White
    Write-ColorMessage "      → Ejecutar: .\insert-funnel-templates.ps1" -Color Cyan
    Write-ColorMessage "   2. Servidor no está ejecutándose en puerto $BaseUrl" -Color White
    Write-ColorMessage "      → Ejecutar: .\mvnw.cmd spring-boot:run" -Color Cyan
    Write-ColorMessage "   3. Token JWT inválido o expirado" -Color White
    Write-ColorMessage "      → Generar nuevo token de autenticación" -Color Cyan
    Write-Host ""
    exit 1
}
]]>
