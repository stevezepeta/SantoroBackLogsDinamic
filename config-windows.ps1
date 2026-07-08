# CONFIGURACION PARA WINDOWS - Backend Principal de Logs
# Ejecutar este script en PowerShell para configurar las variables de entorno

Write-Host "Configurando variables de entorno para Backend Principal..." -ForegroundColor Yellow
Write-Host ""

# Servidor
$env:SERVER_PORT = "8040"

# Base de datos MongoDB
$env:MONGODB_URI = "mongodb://localhost:27017/logs_system"
$env:MULTITENANT_BASE_DATABASE = "logs_system"

# WebSocket
$env:WEBSOCKET_ALLOWED_ORIGINS = "http://localhost:3000,https://dashboard-api.grupo-santoro.com.mx"

# JWT (CAMBIAR EN PRODUCCION)
$env:JWT_SECRET = "tu_secret_super_seguro_minimo_32_caracteres_cambiar_en_produccion_12345678"

# Correo (opcional)
$env:MAIL_HOST = "smtp.gmail.com"
$env:MAIL_PORT = "587"
$env:MAIL_USERNAME = "soporte.tecnico@grupo-santoro.com.mx"
# $env:MAIL_PASSWORD = "tu_app_password_aqui"

# Perfil de Spring
$env:SPRING_PROFILES_ACTIVE = "prod,mail"

Write-Host "Variables configuradas:" -ForegroundColor Green
Write-Host "  SERVER_PORT: $env:SERVER_PORT" -ForegroundColor White
Write-Host "  MONGODB_URI: $env:MONGODB_URI" -ForegroundColor White
Write-Host "  MULTITENANT_BASE_DATABASE: $env:MULTITENANT_BASE_DATABASE" -ForegroundColor White
Write-Host ""
Write-Host "Para ejecutar el servidor:" -ForegroundColor Yellow
Write-Host "  .\mvnw.cmd spring-boot:run" -ForegroundColor White
Write-Host ""
Write-Host "O con el JAR:" -ForegroundColor Yellow
Write-Host "  java -jar target\dinamico-0.0.1-SNAPSHOT.jar" -ForegroundColor White
Write-Host ""

