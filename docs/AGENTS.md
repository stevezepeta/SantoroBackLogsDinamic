# AGENTS.md

## Project

Spring Boot 3.5.6 (Java 17) REST API for centralized log management, dashboards, and AI-powered operational analysis ("Eva"). Uses MongoDB (multi-tenant), Spring Security (JWT + API key), Spring AI (OpenAI), WebSocket (STOMP), and biometric auth (SourceAFIS).

## Build & Run

```bash
# Build JAR (tests skipped — the repo has only a single context-loads test)
.\mvnw.cmd clean package -DskipTests

# Build with tests (requires running MongoDB on localhost:27017)
.\mvnw.cmd clean package

# Run
.\mvnw.cmd spring-boot:run
# or
java -jar target/dinamico-0.0.1-SNAPSHOT.jar
```

There is also `buil-jar.bat` (note the typo) which runs `mvn clean package -DskipTests`.

## Required Environment

| Variable | Purpose |
|---|---|
| `OPENAI_API_KEY` | Spring AI / OpenAI |
| `EVA_OPENAI_API_KEY` | Eva assistant OpenAI key |
| MongoDB on `localhost:27017` | Default connection; override with `MONGODB_URI` |
| `SERVER_PORT` | Prod port (default 8005) |

Firebase push notifications require `src/main/resources/firebase-service-account.json` (gitignored).

## Package Layout

Base package: `backlogs.dinamico`

| Package | Role |
|---|---|
| `controller/` | REST endpoints grouped by domain (`auth/`, `core/`, `catalog/`, `logs/`, `ai/`, `biometric/`, `santoro/`, `notifications/`) |
| `service/` | Business logic, mirrors controller grouping |
| `model/` | MongoDB `@Document` entities; all extend `BaseEntity` (ObjectId id, timestamps) |
| `repository/` | Spring Data MongoDB repositories |
| `infra/security/` | `JwtAuthFilter`, `JwtTokenService`, `ApiKeyTenantFilter` |
| `tenant/` | Multi-tenancy: `TenantContext` (ThreadLocal), `TenantRoutingMongoDbFactory`, resolution filters |
| `security/auth/` | RBAC: `AuthorizationContext`, `ScopeGuard` |
| `config/` | Spring `@Configuration` beans (Security, OpenAPI, Mongo auditing, mail, rate-limit, Firebase) |
| `error/` | `GlobalExceptionHandler` (`@RestControllerAdvice`) and custom exceptions |
| `api/dto/` | Request/response DTOs |

## Key Architecture Details

- **Multi-tenancy** is configurable via `app.multitenant.strategy`: `single`, `collection-per-tenant`, `database-per-tenant`. Tenant resolved from `X-Tenant` / `X-Organization-Id` headers or API key. Dev profile uses `collection-per-tenant`.
- **Dual auth**: JWT Bearer tokens (UI users) and `X-Api-Key` header (log ingest). Filter chain: `ApiKeyTenantFilter` → `TenantResolutionFilter` → `JwtAuthFilter`.
- **All responses** wrapped in `ApiResponse<T>` record.
- **Lombok** used everywhere (`@Data`, `@Builder`, `@RequiredArgsConstructor`, `@Slf4j`). The Maven compiler plugin has the Lombok annotation processor configured.
- **Rate limiting**: double-layer Bucket4j (per API-key + per tenant) with Caffeine caches.
- **AI ("Eva")**: Spring AI with GPT-4o-mini. Streaming SSE responses. Scheduled jobs for alerts, weekly reports, log retention.
- **WebSocket**: STOMP over `/ws`, broker prefix `/topic` for real-time dashboard updates.
- **PDF generation**: Flying Saucer + OpenPDF for alert/weekly reports.
- **CORS**: Custom headers (`X-Tenant`, `X-Tenant-Id`, `X-Organization-Id`) are explicitly exposed in `SecurityConfig.corsConfigurationSource()` to prevent 403s in AWS ALB/proxy environments. See `docs/CORS_AWS_FIX.md`.

## Conventions

- Code is in **English**; comments, error messages, and git commits are in **Spanish**.
- Swagger UI available at `/swagger-ui.html`, grouped into Admin / Core / Public.
- MongoDB aggregation pipelines are used heavily for dashboard analytics (see `LogDashboardService`).
- `data/fp/` stores fingerprint images: `tenantId/curp/finger_timestamp.jpg`.

## Gotchas

- `application.properties` has hardcoded SMTP credentials and JWT secret — treat as sensitive; do not copy these values into new files or logs.
- Test coverage is minimal (single `contextLoads` test). Any new service will likely need MongoDB running to test.
- No CI pipeline exists. No linter or formatter config beyond IDE defaults.
- The build script filename has a typo: `buil-jar.bat` (not `build-jar.bat`).
