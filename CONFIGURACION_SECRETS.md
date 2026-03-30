# Configuración de Secrets para Producción

## Variables de Entorno Requeridas

Para ejecutar la aplicación en **producción**, necesitas configurar las siguientes variables de entorno (secrets):

### 1. OpenAI API
```bash
OPENAI_API_KEY=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
```

### 2. Email (SMTP Gmail)
```bash
MAIL_PASSWORD=wnnkjroexgypcpss
MAIL_FROM=soporte.tecnico@grupo-santoro.com.mx
```

### 3. MongoDB (Nuevo - REQUERIDO para Producción)
```bash
MONGODB_URI=mongodb+srv://usuario:password@cluster.mongodb.net/nombre_base_datos?retryWrites=true&w=majority
MONGODB_DATABASE=backlogs
```

**Formato de la URI de MongoDB:**
- **MongoDB Atlas:** `mongodb+srv://usuario:password@cluster.mongodb.net/basedatos?retryWrites=true&w=majority`
- **MongoDB Local:** `mongodb://localhost:27017/backlogs_prod`
- **MongoDB con autenticación:** `mongodb://usuario:password@host:27017/database?authSource=admin`

**⚠️ IMPORTANTE:** La URI debe incluir el nombre de la base de datos después del host y antes de los parámetros de query.

### 4. Puerto del Servidor (Opcional)
```bash
SERVER_PORT=8005
```

---

## Configuración por Ambiente

### Desarrollo (Local)
- **Perfil:** `dev`
- **MongoDB:** Usa conexión local `mongodb://localhost:27017/backlogs_dev`
- **Variables de entorno:** Solo necesitas `OPENAI_API_KEY`, `MAIL_PASSWORD` y `MAIL_FROM`

### Producción
- **Perfil:** `prod`
- **MongoDB:** Usa la URI de producción desde variable de entorno
- **Variables de entorno:** Todas las variables listadas arriba son requeridas

---

## Cómo Configurar en Diferentes Entornos

### GitHub Actions / GitHub Secrets
```yaml
env:
  OPENAI_API_KEY: ${{ secrets.OPENAI_API_KEY }}
  MAIL_PASSWORD: ${{ secrets.MAIL_PASSWORD }}
  MAIL_FROM: ${{ secrets.MAIL_FROM }}
  MONGODB_URI: ${{ secrets.MONGODB_URI }}
  MONGODB_DATABASE: ${{ secrets.MONGODB_DATABASE }}
  SERVER_PORT: ${{ secrets.SERVER_PORT }}
```

### Docker / Docker Compose
```yaml
environment:
  - OPENAI_API_KEY=${OPENAI_API_KEY}
  - MAIL_PASSWORD=${MAIL_PASSWORD}
  - MAIL_FROM=${MAIL_FROM}
  - MONGODB_URI=${MONGODB_URI}
  - MONGODB_DATABASE=${MONGODB_DATABASE}
  - SERVER_PORT=8005
```

### Variables de Sistema (Windows)
```powershell
setx MONGODB_URI "mongodb://usuario:password@host:27017/backlogs_prod"
setx MONGODB_DATABASE "backlogs_prod"
```

### Variables de Sistema (Linux/Mac)
```bash
export MONGODB_URI="mongodb://usuario:password@host:27017/backlogs_prod"
export MONGODB_DATABASE="backlogs_prod"
```

---

## Ejecutar la Aplicación

### Modo Desarrollo
```bash
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

### Modo Producción
```bash
mvn spring-boot:run -Dspring-boot.run.profiles=prod
```

O al ejecutar el JAR:
```bash
java -jar backlogs.jar --spring.profiles.active=prod
```

---

## Notas de Seguridad

⚠️ **IMPORTANTE:**
- Nunca subas archivos `.env` o archivos con credenciales reales al repositorio
- Usa GitHub Secrets o un gestor de secrets para producción
- El archivo `.env.example` está incluido como plantilla (sin valores reales)
- Asegúrate de que `.env` esté en el `.gitignore`
