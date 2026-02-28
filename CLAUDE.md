# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

NextGenManager is a Spring Boot 3.3.5 (Java 17) ERP backend for manufacturing businesses. It manages inventory, BOM (Bill of Materials), production work orders, routing, work centers, machine assets, sales, and marketing. The API is consumed by a separate frontend (expected at `http://localhost:3000`).

## Commands

### Local Development
```bash
# Run locally (uses application-local.properties with local PostgreSQL)
./mvnw spring-boot:run -Dspring-boot.run.profiles=local

# Build JAR (skip tests)
./mvnw clean package -DskipTests

# Run all tests
./mvnw test

# Run a single test class
./mvnw test -Dtest=MachineDetailsServiceImplTest

# Run a single test method
./mvnw test -Dtest=MachineDetailsServiceImplTest#methodName
```

### Docker
```bash
# Start app + PostgreSQL via docker-compose
docker-compose up

# Build Docker image only
docker build -t nextgenmanager .
```

### Local DB Setup
The `application-local.properties` expects PostgreSQL at `localhost:5432`, database `nextgenmanager`, user `postgres`. Use `docker-compose up postgres` to start just the DB container.

### API Documentation
Swagger UI is available at `http://localhost:8080/swagger-ui/index.html` when running locally. The `/api/auth/login` endpoint is public; all other endpoints require a `Bearer` JWT.

## Architecture

### Module Structure
Code is organized under `src/main/java/com/nextgenmanager/nextgenmanager/` by domain module:

| Module | Package | Description |
|--------|---------|-------------|
| `common` | `common/` | Auth, JWT security, global exception handling, shared models |
| `assets` | `assets/` | Machine details, events, production logs, status history |
| `bom` | `bom/` | Bill of Materials with workflow, audit, history, PDF export |
| `production` | `production/` | Work orders, routings, work centers, production jobs |
| `items` | `items/` | Inventory item master with attachments and item codes |
| `Inventory` | `Inventory/` | Inventory instances, requests, procurement orders |
| `marketing` | `marketing/` | Enquiries and quotations |
| `sales` | `sales/` | Sales orders with PDF invoice generation |
| `contact` | `contact/` | Customer/supplier contacts |
| `employee` | `employee/` | Employee records |
| `config` | `config/` | Spring Security, CORS, MinIO, OpenAPI, Thymeleaf |

Each module follows a consistent layered pattern:
- `model/` — JPA entities
- `repository/` — Spring Data JPA repositories
- `service/` — interface + `ServiceImpl` implementation
- `controller/` — REST controllers (`@RestController`)
- `dto/` — request/response DTOs (often Java records)
- `mapper/` — MapStruct mappers

### Security
- Stateless JWT authentication via `JwtAuthenticationFilter` (before `UsernamePasswordAuthenticationFilter`)
- Access tokens (default 15 min) + refresh tokens (default 7 days) stored in DB
- Roles: `ROLE_SUPER_ADMIN`, `ROLE_ADMIN`, plus custom roles
- Method-level authorization via `@PreAuthorize`
- Public endpoints: `/api/auth/login`, `/api/auth/refresh`, `/swagger-ui/**`, `/v3/api-docs/**`

### Database
- **PostgreSQL** only
- **Flyway** manages schema migrations in `src/main/resources/db/migration/` using sequential `V{n}__description.sql` naming
- When adding schema changes, create a new migration file — never edit existing ones
- Entities use `PhysicalNamingStrategyStandardImpl` (column names match field names exactly, no snake_case conversion)
- Soft deletes are used throughout: entities have a `deletedDate` field; active records are filtered with `IsNull` conditions

### Key Patterns

**Service interface + Impl**: Every service has an interface (e.g., `BomService`) and an implementation (e.g., `BomServiceImpl`). Wire by interface type.

**MapStruct with Lombok**: Both are annotation processors configured in `pom.xml`. The `lombok-mapstruct-binding` ensures correct processing order. Mappers use `@Mapper(componentModel = "spring")`.

**Spring Events for audit/history**: Domain changes publish events (e.g., `BomCreatedEvent`, `BomStatusChangedEvent`) which are handled by listener classes (`BomAuditListener`, `BomHistoryListener`) to write audit and history records.

**File storage**: MinIO is used for file attachments. Config via `minio.*` properties. The `FileStorageService` handles uploads/downloads.

**PDF generation**: Uses OpenHTMLtoPDF + Thymeleaf templates in `src/main/resources/templates/`.

**Filtering/search**: Generic `Specification`-based filtering (`GenericSpecification`, `BomSpecifications`) supports dynamic queries via `FilterRequest`/`FilterCriteria` DTOs.

### Configuration Profiles
- **Default** (`application.properties`): reads from environment variables (`DATASOURCE_URL`, `DATASOURCE_USERNAME`, `DATASOURCE_PASSWORD`, `MINIO_*`, `JWT_*`, `FRONTEND_URL`). Used in Docker/production. Flyway is enabled.
- **local** (`application-local.properties`): hardcoded local DB credentials, `ddl-auto=update`, Flyway disabled. Use for rapid local dev.
- **docker** (`application-docker.properties`): connects to `postgres-container` hostname.