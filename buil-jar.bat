@echo off
title Generador de JAR - %CD%
echo ============================================
echo   Generando archivo JAR (Saltando Tests)
echo ============================================

:: Verificamos si existe el archivo pom.xml en la carpeta actual
if not exist "pom.xml" (
    echo [ERROR] No se encontro el archivo pom.xml. 
    echo Asegurate de poner este .bat en la raiz de tu proyecto.
    pause
    exit /b
)

:: Ejecutamos Maven
call mvn clean package -DskipTests

:: Verificamos si la compilacion fue exitosa
if %ERRORLEVEL% NEQ 0 (
    echo.
    echo ============================================
    echo   [ERROR] Hubo un problema al compilar.
    echo ============================================
    pause
    exit /b
)

echo.
echo ============================================
echo   [OK] JAR generado con exito en /target
echo ============================================
pause