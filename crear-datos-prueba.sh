#!/bin/bash

# Script para crear datos de prueba en la API de Backlogs
# Uso: ./crear-datos-prueba.sh http://tu-ip:8005

API_URL="${1:-http://localhost:8005}"

echo "=================================================="
echo "Creando datos de prueba en: $API_URL"
echo "=================================================="
echo ""

echo "1. Verificando health check..."
curl -s "$API_URL/api/health" | jq '.'
echo ""
echo ""

echo "2. Creando Backlog 1 - ALTA prioridad..."
curl -s -X POST "$API_URL/api/backlogs" \
  -H "Content-Type: application/json" \
  -d '{
    "titulo": "Implementar autenticación JWT",
    "descripcion": "Agregar sistema de autenticación con tokens JWT para securizar la API",
    "prioridad": "ALTA",
    "estado": "EN_PROGRESO",
    "asignadoA": "Alan Dev"
  }' | jq '.'
echo ""

echo "3. Creando Backlog 2 - MEDIA prioridad..."
curl -s -X POST "$API_URL/api/backlogs" \
  -H "Content-Type: application/json" \
  -d '{
    "titulo": "Diseñar UI principal",
    "descripcion": "Crear mockups de pantallas principales del sistema",
    "prioridad": "MEDIA",
    "estado": "POR_HACER",
    "asignadoA": "Diseñador UX"
  }' | jq '.'
echo ""

echo "4. Creando Backlog 3 - ALTA prioridad..."
curl -s -X POST "$API_URL/api/backlogs" \
  -H "Content-Type: application/json" \
  -d '{
    "titulo": "Configurar CI/CD pipeline",
    "descripcion": "Implementar pipeline de despliegue automático con GitHub Actions",
    "prioridad": "ALTA",
    "estado": "EN_PROGRESO",
    "asignadoA": "DevOps Team"
  }' | jq '.'
echo ""

echo "5. Creando Backlog 4 - BAJA prioridad..."
curl -s -X POST "$API_URL/api/backlogs" \
  -H "Content-Type: application/json" \
  -d '{
    "titulo": "Actualizar documentación",
    "descripcion": "Mejorar documentación de API y agregar ejemplos",
    "prioridad": "BAJA",
    "estado": "POR_HACER",
    "asignadoA": "Tech Writer"
  }' | jq '.'
echo ""

echo "6. Creando Backlog 5 - COMPLETADO..."
curl -s -X POST "$API_URL/api/backlogs" \
  -H "Content-Type: application/json" \
  -d '{
    "titulo": "Migrar a MongoDB Atlas",
    "descripcion": "Migración exitosa de base de datos a MongoDB Atlas en producción",
    "prioridad": "ALTA",
    "estado": "COMPLETADO",
    "asignadoA": "Backend Team"
  }' | jq '.'
echo ""

echo "=================================================="
echo "✅ Datos de prueba creados exitosamente"
echo "=================================================="
echo ""
echo "Lista de todos los backlogs:"
curl -s "$API_URL/api/backlogs" | jq '.'
