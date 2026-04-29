@echo off
echo ============================================
echo Configurando variables de entorno para PRODUCCION
echo ============================================

REM Cargar variables desde archivo .env local (debe existir)
if not exist ".env" (
    echo [ERROR] No se encontro el archivo .env
    echo Por favor, copia .env.example a .env y configura tus credenciales
    pause
    exit /b 1
)

REM Leer variables del archivo .env
for /f "usebackq tokens=1,* delims==" %%a in (".env") do (
    set "%%a=%%b"
)

echo.
echo Variables de entorno configuradas correctamente:
echo - MONGODB_URI: mongodb+srv://alandev:****@cluster0.wl0b8lf.mongodb.net/
echo - MONGODB_DATABASE: backlogs
echo - OPENAI_API_KEY: Configurado
echo - MAIL: Configurado
echo - SERVER_PORT: 8005
echo.
echo ============================================
echo Iniciando aplicacion en modo PRODUCCION
echo ============================================
echo.

java -jar target\dinamico-0.0.1-SNAPSHOT.jar --spring.profiles.active=prod

pause
