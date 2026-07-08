# Verificar Configuracion Principal - Backend de Logs
# Este script verifica que la configuracion este correcta para el ambiente principal

param(
    [switch]$Verbose
)

Write-Host "================================================================" -ForegroundColor Cyan
Write-Host "  VERIFICACION DE CONFIGURACION - BACKEND PRINCIPAL DE LOGS" -ForegroundColor Cyan
Write-Host "================================================================" -ForegroundColor Cyan
Write-Host ""

$errores = 0
$advertencias = 0

# ================================================================
# FUNCION AUXILIAR: Verificar contenido de archivo
# ================================================================
function Verificar-Contenido {
    param(
        [string]$Archivo,
        [string]$TextoEsperado,
        [string]$Descripcion,
        [switch]$EsAdvertencia
    )

    if (Test-Path $Archivo) {
        $contenido = Get-Content $Archivo -Raw -Encoding UTF8
        if ($contenido -match [regex]::Escape($TextoEsperado)) {
            Write-Host "  [OK] $Descripcion" -ForegroundColor Green
            return $true
        } else {
            if ($EsAdvertencia) {
                Write-Host "  [WARN] $Descripcion - No encontrado" -ForegroundColor Yellow
                $script:advertencias++
            } else {
                Write-Host "  [ERROR] $Descripcion - No encontrado" -ForegroundColor Red
                $script:errores++
            }
            return $false
        }
    } else {
        Write-Host "  [ERROR] Archivo no encontrado: $Archivo" -ForegroundColor Red
        $script:errores++
        return $false
    }
}

# ================================================================
# 1. VERIFICAR PUERTO 8040
# ================================================================
Write-Host "1. VERIFICANDO PUERTO DEL SERVIDOR (debe ser 8040)..." -ForegroundColor Yellow
Write-Host ""

Verificar-Contenido `
    -Archivo "src\main\resources\application.yml" `
    -TextoEsperado "port: `${SERVER_PORT:8040}" `
    -Descripcion "application.yml - Puerto 8040"

Verificar-Contenido `
    -Archivo "src\main\resources\application-prod.properties" `
    -TextoEsperado "SERVER_PORT:8040" `
    -Descripcion "application-prod.properties - Puerto 8040"

Write-Host ""

# ================================================================
# 2. VERIFICAR BASE DE DATOS logs_system
# ================================================================
Write-Host "2. VERIFICANDO BASE DE DATOS (debe ser logs_system)..." -ForegroundColor Yellow
Write-Host ""

Verificar-Contenido `
    -Archivo "src\main\resources\application.yml" `
    -TextoEsperado "mongodb://localhost:27017/logs_system" `
    -Descripcion "application.yml - MongoDB URI logs_system"

Verificar-Contenido `
    -Archivo "src\main\resources\application.yml" `
    -TextoEsperado "base-database: `${MULTITENANT_BASE_DATABASE:logs_system}" `
    -Descripcion "application.yml - Multitenant base-database logs_system"

Verificar-Contenido `
    -Archivo "src\main\resources\application-dev.properties" `
    -TextoEsperado "mongodb://localhost:27017/logs_system" `
    -Descripcion "application-dev.properties - MongoDB URI logs_system"

Verificar-Contenido `
    -Archivo "src\main\resources\application-dev.yml" `
    -TextoEsperado "base-database: `${MULTITENANT_BASE_DATABASE:logs_system}" `
    -Descripcion "application-dev.yml - Multitenant base-database logs_system"

Write-Host ""

# ================================================================
# 3. VERIFICAR WEBSOCKET
# ================================================================
Write-Host "3. VERIFICANDO CONFIGURACION DE WEBSOCKET..." -ForegroundColor Yellow
Write-Host ""

Verificar-Contenido `
    -Archivo "src\main\resources\application.properties" `
    -TextoEsperado "AMBIENTE PRINCIPAL (Puerto 8040)" `
    -Descripcion "application.properties - Comentario de ambiente principal"

Verificar-Contenido `
    -Archivo "src\main\resources\application.properties" `
    -TextoEsperado "dashboard-api.grupo-santoro.com.mx" `
    -Descripcion "application.properties - WebSocket origen principal"

Write-Host ""

# ================================================================
# 4. VERIFICAR AUSENCIA DE REFERENCIAS A QUINTANA ROO
# ================================================================
Write-Host "4. VERIFICANDO AUSENCIA DE REFERENCIAS A QUINTANA ROO..." -ForegroundColor Yellow
Write-Host ""

$archivos = @(
    "src\main\resources\application.yml",
    "src\main\resources\application.properties",
    "src\main\resources\application-dev.properties",
    "src\main\resources\application-dev.yml",
    "src\main\resources\application-prod.properties"
)

$referenciasQR = @("logsQR", "8057", "quintanaroo", "Quintana Roo")
$encontradoQR = $false

foreach ($archivo in $archivos) {
    if (Test-Path $archivo) {
        $contenido = Get-Content $archivo -Raw -Encoding UTF8
        foreach ($ref in $referenciasQR) {
            if ($contenido -match $ref -and $contenido -notmatch "NO.*$ref") {
                Write-Host "  [WARN] Referencia a '$ref' encontrada en $archivo" -ForegroundColor Yellow
                $script:advertencias++
                $encontradoQR = $true

                if ($Verbose) {
                    $lineas = Get-Content $archivo | Select-String $ref
                    foreach ($linea in $lineas) {
                        Write-Host "    Linea $($linea.LineNumber): $($linea.Line)" -ForegroundColor Gray
                    }
                }
            }
        }
    }
}

if (-not $encontradoQR) {
    Write-Host "  [OK] No se encontraron referencias a Quintana Roo" -ForegroundColor Green
}

Write-Host ""

# ================================================================
# 5. VERIFICAR NOMBRE DE CORREO
# ================================================================
Write-Host "5. VERIFICANDO CONFIGURACION DE CORREO..." -ForegroundColor Yellow
Write-Host ""

Verificar-Contenido `
    -Archivo "src\main\resources\application.properties" `
    -TextoEsperado "Sistema Principal" `
    -Descripcion "application.properties - Nombre de remitente correcto"

Write-Host ""

# ================================================================
# 6. VERIFICAR VARIABLES DE ENTORNO (OPCIONAL)
# ================================================================
Write-Host "6. VERIFICANDO VARIABLES DE ENTORNO..." -ForegroundColor Yellow
Write-Host ""

if ($env:SERVER_PORT) {
    if ($env:SERVER_PORT -eq "8040") {
        Write-Host "  [OK] SERVER_PORT = $env:SERVER_PORT" -ForegroundColor Green
    } else {
        Write-Host "  [WARN] SERVER_PORT = $env:SERVER_PORT (esperado: 8040)" -ForegroundColor Yellow
        $script:advertencias++
    }
} else {
    Write-Host "  [INFO] SERVER_PORT no configurada (usara valor por defecto: 8040)" -ForegroundColor Gray
}

if ($env:MULTITENANT_BASE_DATABASE) {
    if ($env:MULTITENANT_BASE_DATABASE -eq "logs_system") {
        Write-Host "  [OK] MULTITENANT_BASE_DATABASE = $env:MULTITENANT_BASE_DATABASE" -ForegroundColor Green
    } else {
        Write-Host "  [WARN] MULTITENANT_BASE_DATABASE = $env:MULTITENANT_BASE_DATABASE (esperado: logs_system)" -ForegroundColor Yellow
        $script:advertencias++
    }
} else {
    Write-Host "  [INFO] MULTITENANT_BASE_DATABASE no configurada (usara valor por defecto: logs_system)" -ForegroundColor Gray
}

if ($env:MONGODB_URI) {
    if ($env:MONGODB_URI -match "logs_system") {
        Write-Host "  [OK] MONGODB_URI contiene 'logs_system'" -ForegroundColor Green
    } else {
        Write-Host "  [WARN] MONGODB_URI = $env:MONGODB_URI (no contiene 'logs_system')" -ForegroundColor Yellow
        $script:advertencias++
    }
} else {
    Write-Host "  [INFO] MONGODB_URI no configurada (usara valor por defecto)" -ForegroundColor Gray
}

Write-Host ""

# ================================================================
# RESUMEN FINAL
# ================================================================
Write-Host "================================================================" -ForegroundColor Cyan
Write-Host "  RESUMEN DE VERIFICACION" -ForegroundColor Cyan
Write-Host "================================================================" -ForegroundColor Cyan
Write-Host ""

if ($errores -eq 0 -and $advertencias -eq 0) {
    Write-Host "  ESTADO: PERFECTO" -ForegroundColor Green
    Write-Host "  Todos los parametros de configuracion son correctos" -ForegroundColor Green
    Write-Host ""
    Write-Host "  La configuracion esta lista para:" -ForegroundColor White
    Write-Host "    - Puerto: 8040" -ForegroundColor Gray
    Write-Host "    - Base de datos: logs_system" -ForegroundColor Gray
    Write-Host "    - WebSocket: dashboard-api.grupo-santoro.com.mx" -ForegroundColor Gray
    Write-Host ""
    Write-Host "  Proximos pasos:" -ForegroundColor Yellow
    Write-Host "    1. Compilar: .\mvnw.cmd clean package -DskipTests" -ForegroundColor White
    Write-Host "    2. Ejecutar: .\mvnw.cmd spring-boot:run" -ForegroundColor White
    Write-Host "    3. Verificar: http://localhost:8040/swagger-ui.html" -ForegroundColor White
    Write-Host ""
    exit 0
} elseif ($errores -eq 0) {
    Write-Host "  ESTADO: CORRECTO CON ADVERTENCIAS" -ForegroundColor Yellow
    Write-Host "  Errores: 0" -ForegroundColor Green
    Write-Host "  Advertencias: $advertencias" -ForegroundColor Yellow
    Write-Host ""
    Write-Host "  La configuracion es funcional pero hay advertencias menores" -ForegroundColor Yellow
    Write-Host "  Revisar advertencias arriba y corregir si es necesario" -ForegroundColor Yellow
    Write-Host ""
    exit 0
} else {
    Write-Host "  ESTADO: ERRORES ENCONTRADOS" -ForegroundColor Red
    Write-Host "  Errores: $errores" -ForegroundColor Red
    Write-Host "  Advertencias: $advertencias" -ForegroundColor Yellow
    Write-Host ""
    Write-Host "  ACCION REQUERIDA:" -ForegroundColor Red
    Write-Host "    Corregir los errores arriba antes de compilar" -ForegroundColor Red
    Write-Host ""
    Write-Host "  Documentacion:" -ForegroundColor Yellow
    Write-Host "    docs\RESTAURACION_CONFIG_PRINCIPAL.md" -ForegroundColor White
    Write-Host ""
    exit 1
}

