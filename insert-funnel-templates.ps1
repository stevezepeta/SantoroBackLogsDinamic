<![CDATA[# ═══════════════════════════════════════════════════════════════════════════
# Script: insert-funnel-templates.ps1
# Descripción: Inserta los templates de funnel en MongoDB de forma automática
# Fecha: 2026-06-16
# ═══════════════════════════════════════════════════════════════════════════

param(
    [string]$MongoUri = "mongodb://localhost:27017",
    [string]$Database = "logs_system",
    [switch]$DeleteExisting = $false,
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

# ─────────────────────────────────────────────────────────────────────────────
# SCRIPT PRINCIPAL
# ─────────────────────────────────────────────────────────────────────────────

Write-Section "INSERCIÓN DE FUNNEL TEMPLATES EN MONGODB"

Write-ColorMessage "📊 Parámetros de Conexión:" -Color Green
Write-ColorMessage "   MongoDB URI: $MongoUri" -Color White
Write-ColorMessage "   Database: $Database" -Color White
Write-ColorMessage "   Eliminar existentes: $DeleteExisting" -Color White
Write-Host ""

# Verificar que mongosh esté instalado
Write-ColorMessage "🔍 Verificando MongoDB Shell (mongosh)..." -Color Yellow

try {
    $mongoshVersion = & mongosh --version 2>&1
    Write-ColorMessage "✅ mongosh encontrado: $mongoshVersion" -Color Green
} catch {
    Write-ColorMessage "❌ ERROR: mongosh no está instalado o no está en el PATH" -Color Red
    Write-ColorMessage "" -Color White
    Write-ColorMessage "📥 Descarga mongosh desde:" -Color Yellow
    Write-ColorMessage "   https://www.mongodb.com/try/download/shell" -Color Cyan
    exit 1
}

# Crear el script MongoDB
$mongoScript = @"
use $Database;

"@ + $(if ($DeleteExisting) {
@"

print('\n⚠️  ELIMINANDO templates existentes...');
var deleteResult = db.funnel_templates.deleteMany({});
print('   Documentos eliminados: ' + deleteResult.deletedCount);

"@
}) + @"

print('\n📝 INSERTANDO nuevos templates...\n');

var insertResult = db.funnel_templates.insertMany([

  // ──────────────────────────────────────────────────────────────────────────
  // 1. TRUSTVALUE - Flujo de Jornada Laboral (FLUJO REAL)
  // ──────────────────────────────────────────────────────────────────────────
  {
    "systemName": "TRUSTVALUE",
    "funnelName": "Flujo de Jornada Laboral",
    "description": "Flujo completo de registro de asistencia de empleados en TrustValue",
    "active": true,
    "createdAt": new Date(),
    "updatedAt": new Date(),
    "createdBy": "system",
    "steps": [
      {
        "order": 1,
        "eventType": "INICIO_SESION",
        "label": "Inicio de Sesión",
        "description": "Usuario inicia sesión en el sistema"
      },
      {
        "order": 2,
        "eventType": "SELECCION_SUCURSAL",
        "label": "Selección de Sucursal",
        "description": "Usuario selecciona la sucursal donde laborará"
      },
      {
        "order": 3,
        "eventType": "ENVIAR_EVIDENCIAS",
        "label": "Envío de Evidencias",
        "description": "Usuario envía evidencias fotográficas o biométricas"
      },
      {
        "order": 4,
        "eventType": "FINALIZAR_ASISTENCIA",
        "label": "Cierre de Jornada",
        "description": "Registro de asistencia completado exitosamente"
      }
    ]
  },

  // ──────────────────────────────────────────────────────────────────────────
  // 2. CITA_GUYANA - Flujo de Tramitación de Citas
  // ──────────────────────────────────────────────────────────────────────────
  {
    "systemName": "CITA_GUYANA",
    "funnelName": "Flujo de Trámite de Cita",
    "description": "Proceso completo de agendamiento de citas consulares en Guyana",
    "active": true,
    "createdAt": new Date(),
    "updatedAt": new Date(),
    "createdBy": "system",
    "steps": [
      {
        "order": 1,
        "eventType": "AUTH_LOGIN",
        "label": "Inicio de Sesión",
        "description": "Usuario se autentica en el sistema"
      },
      {
        "order": 2,
        "eventType": "CONSULTA_ESTADO_REGISTRO",
        "label": "Validación de Registro",
        "description": "Sistema valida el registro del usuario"
      },
      {
        "order": 3,
        "eventType": "RESERVA_DE_CITA",
        "label": "Cita Completada",
        "description": "Usuario completa la reserva de su cita"
      }
    ]
  },

  // ──────────────────────────────────────────────────────────────────────────
  // 3. TICKETS - Flujo de Atención de Tickets de Soporte
  // ──────────────────────────────────────────────────────────────────────────
  {
    "systemName": "TICKETS",
    "funnelName": "Flujo de Atención de Tickets",
    "description": "Ciclo completo de gestión de tickets de soporte técnico",
    "active": true,
    "createdAt": new Date(),
    "updatedAt": new Date(),
    "createdBy": "system",
    "steps": [
      {
        "order": 1,
        "eventType": "CREAR_TICKET",
        "label": "Creación de Ticket",
        "description": "Usuario crea un nuevo ticket de soporte"
      },
      {
        "order": 2,
        "eventType": "ASIGNAR_TECNICO",
        "label": "Asignación a Técnico",
        "description": "Ticket es asignado a un técnico disponible"
      },
      {
        "order": 3,
        "eventType": "RESOLVER_TICKET",
        "label": "Ticket Resuelto",
        "description": "Técnico marca el ticket como resuelto"
      }
    ]
  },

  // ──────────────────────────────────────────────────────────────────────────
  // 4. PASSPORT - Flujo de Tramitación de Pasaportes
  // ──────────────────────────────────────────────────────────────────────────
  {
    "systemName": "PASSPORT",
    "funnelName": "Flujo de Tramitación de Pasaporte",
    "description": "Proceso completo de solicitud y emisión de pasaportes",
    "active": true,
    "createdAt": new Date(),
    "updatedAt": new Date(),
    "createdBy": "system",
    "steps": [
      {
        "order": 1,
        "eventType": "SOLICITUD_PASAPORTE",
        "label": "Solicitud Iniciada",
        "description": "Ciudadano inicia solicitud de pasaporte"
      },
      {
        "order": 2,
        "eventType": "VALIDACION_DOCUMENTOS",
        "label": "Documentos Validados",
        "description": "Documentos presentados son validados"
      },
      {
        "order": 3,
        "eventType": "APROBACION_PASAPORTE",
        "label": "Pasaporte Aprobado",
        "description": "Solicitud es aprobada por autoridades"
      },
      {
        "order": 4,
        "eventType": "EMISION_PASAPORTE",
        "label": "Pasaporte Emitido",
        "description": "Pasaporte físico es emitido y entregado"
      }
    ]
  },

  // ──────────────────────────────────────────────────────────────────────────
  // 5. CITA_QUINTANAROO - Flujo de Citas Quintana Roo
  // ──────────────────────────────────────────────────────────────────────────
  {
    "systemName": "CITA_QUINTANAROO",
    "funnelName": "Flujo de Citas Quintana Roo",
    "description": "Sistema de agendamiento de citas para oficina de Quintana Roo",
    "active": true,
    "createdAt": new Date(),
    "updatedAt": new Date(),
    "createdBy": "system",
    "steps": [
      {
        "order": 1,
        "eventType": "AUTH_LOGIN",
        "label": "Inicio de Sesión",
        "description": "Usuario inicia sesión"
      },
      {
        "order": 2,
        "eventType": "CONSULTA_DISPONIBILIDAD",
        "label": "Consulta de Disponibilidad",
        "description": "Usuario consulta horarios disponibles"
      },
      {
        "order": 3,
        "eventType": "RESERVA_CITA",
        "label": "Cita Reservada",
        "description": "Usuario reserva una cita disponible"
      },
      {
        "order": 4,
        "eventType": "CONFIRMACION_CITA",
        "label": "Cita Confirmada",
        "description": "Sistema confirma la reserva de la cita"
      }
    ]
  }

]);

print('✅ Templates insertados: ' + insertResult.insertedIds.length);

// Crear índice único
print('\n📇 Creando índice único en systemName...');
try {
    db.funnel_templates.createIndex({ "systemName": 1 }, { unique: true });
    print('✅ Índice creado exitosamente');
} catch (e) {
    if (e.code === 85 || e.code === 86) {
        print('⚠️  Índice ya existe');
    } else {
        print('❌ Error al crear índice: ' + e.message);
    }
}

print('\n📊 VERIFICACIÓN FINAL:');
print('─────────────────────────────────────────────────────────────────────────');

var templates = db.funnel_templates.find({ active: true }, { systemName: 1, funnelName: 1, _id: 0 }).sort({ systemName: 1 });

print('\n✅ Sistemas configurados:\n');
templates.forEach(function(t) {
    print('   🔹 ' + t.systemName.padEnd(20) + ' → ' + t.funnelName);
});

print('\n📈 Total de templates activos: ' + db.funnel_templates.countDocuments({ active: true }));
print('─────────────────────────────────────────────────────────────────────────\n');

print('🎉 PROCESO COMPLETADO EXITOSAMENTE\n');
"@

# Guardar el script en un archivo temporal
$tempScriptPath = [System.IO.Path]::GetTempFileName() + ".js"
$mongoScript | Out-File -FilePath $tempScriptPath -Encoding UTF8

Write-ColorMessage "📂 Script temporal creado: $tempScriptPath" -Color Gray

# Ejecutar el script con mongosh
Write-ColorMessage "`n🚀 Ejecutando script en MongoDB..." -Color Yellow
Write-Host ""

try {
    if ($Verbose) {
        & mongosh "$MongoUri/$Database" --file $tempScriptPath
    } else {
        & mongosh "$MongoUri/$Database" --file $tempScriptPath --quiet
    }

    Write-Host ""
    Write-ColorMessage "✅ Script ejecutado exitosamente" -Color Green

} catch {
    Write-ColorMessage "❌ ERROR al ejecutar el script:" -Color Red
    Write-ColorMessage $_.Exception.Message -Color Red
    exit 1
} finally {
    # Limpiar archivo temporal
    if (Test-Path $tempScriptPath) {
        Remove-Item $tempScriptPath -Force
        Write-ColorMessage "🗑️  Archivo temporal eliminado" -Color Gray
    }
}

Write-Host ""
Write-Section "PRÓXIMOS PASOS"

Write-ColorMessage "1️⃣  Compilar el proyecto:" -Color Yellow
Write-ColorMessage "    .\mvnw.cmd clean package -DskipTests" -Color Cyan
Write-Host ""

Write-ColorMessage "2️⃣  Iniciar el servidor:" -Color Yellow
Write-ColorMessage "    .\mvnw.cmd spring-boot:run" -Color Cyan
Write-Host ""

Write-ColorMessage "3️⃣  Probar el endpoint:" -Color Yellow
Write-ColorMessage "    GET http://localhost:8040/api/analytics/funnel-systems/available" -Color Cyan
Write-ColorMessage "    GET http://localhost:8040/api/analytics/funnel/TRUSTVALUE" -Color Cyan
Write-Host ""

Write-ColorMessage "📚 Documentación completa en:" -Color Yellow
Write-ColorMessage "    docs\FUNNEL_TEMPLATES_SEED.md" -Color Cyan
Write-Host ""
]]>
