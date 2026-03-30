#!/bin/bash

echo "============================================"
echo "Configurando variables de entorno para PRODUCCION"
echo "============================================"

# MongoDB Atlas
export MONGODB_URI="mongodb+srv://alandev:q6iLhYog5mtiKTY3@cluster0.wl0b8lf.mongodb.net/backlogs?retryWrites=true&w=majority&appName=Cluster0"
export MONGODB_DATABASE="backlogs"

# OpenAI API
export OPENAI_API_KEY="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"

# Email Configuration
export MAIL_PASSWORD="wnnkjroexgypcpss"
export MAIL_FROM="soporte.tecnico@grupo-santoro.com.mx"

# Server Port
export SERVER_PORT="8005"

echo ""
echo "Variables de entorno configuradas correctamente:"
echo "- MONGODB_URI: mongodb+srv://alandev:****@cluster0.wl0b8lf.mongodb.net/"
echo "- MONGODB_DATABASE: backlogs"
echo "- OPENAI_API_KEY: Configurado"
echo "- MAIL: Configurado"
echo "- SERVER_PORT: 8005"
echo ""
echo "============================================"
echo "Iniciando aplicacion en modo PRODUCCION"
echo "============================================"
echo ""

java -jar target/dinamico-0.0.1-SNAPSHOT.jar --spring.profiles.active=prod
