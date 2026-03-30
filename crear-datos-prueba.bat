@echo off
REM Script para crear datos de prueba en la API de Backlogs
REM Uso: crear-datos-prueba.bat http://tu-ip:8005

setlocal
set API_URL=%1
if "%API_URL%"=="" set API_URL=http://localhost:8005

echo ==================================================
echo Creando datos de prueba en: %API_URL%
echo ==================================================
echo.

echo 1. Verificando health check...
curl -s "%API_URL%/api/health"
echo.
echo.

echo 2. Creando Backlog 1 - ALTA prioridad...
curl -s -X POST "%API_URL%/api/backlogs" ^
  -H "Content-Type: application/json" ^
  -d "{\"titulo\":\"Implementar autenticacion JWT\",\"descripcion\":\"Agregar sistema de autenticacion con tokens JWT\",\"prioridad\":\"ALTA\",\"estado\":\"EN_PROGRESO\",\"asignadoA\":\"Alan Dev\"}"
echo.

echo 3. Creando Backlog 2 - MEDIA prioridad...
curl -s -X POST "%API_URL%/api/backlogs" ^
  -H "Content-Type: application/json" ^
  -d "{\"titulo\":\"Disenar UI principal\",\"descripcion\":\"Crear mockups de pantallas principales\",\"prioridad\":\"MEDIA\",\"estado\":\"POR_HACER\",\"asignadoA\":\"Disenador UX\"}"
echo.

echo 4. Creando Backlog 3 - ALTA prioridad...
curl -s -X POST "%API_URL%/api/backlogs" ^
  -H "Content-Type: application/json" ^
  -d "{\"titulo\":\"Configurar CI/CD\",\"descripcion\":\"Pipeline de despliegue automatico\",\"prioridad\":\"ALTA\",\"estado\":\"EN_PROGRESO\",\"asignadoA\":\"DevOps Team\"}"
echo.

echo 5. Creando Backlog 4 - BAJA prioridad...
curl -s -X POST "%API_URL%/api/backlogs" ^
  -H "Content-Type: application/json" ^
  -d "{\"titulo\":\"Actualizar documentacion\",\"descripcion\":\"Mejorar docs de API\",\"prioridad\":\"BAJA\",\"estado\":\"POR_HACER\",\"asignadoA\":\"Tech Writer\"}"
echo.

echo 6. Creando Backlog 5 - COMPLETADO...
curl -s -X POST "%API_URL%/api/backlogs" ^
  -H "Content-Type: application/json" ^
  -d "{\"titulo\":\"Migrar a MongoDB Atlas\",\"descripcion\":\"Migracion exitosa a produccion\",\"prioridad\":\"ALTA\",\"estado\":\"COMPLETADO\",\"asignadoA\":\"Backend Team\"}"
echo.

echo ==================================================
echo Datos de prueba creados exitosamente
echo ==================================================
echo.
echo Lista de todos los backlogs:
curl -s "%API_URL%/api/backlogs"
echo.

pause
