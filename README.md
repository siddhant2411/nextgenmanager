<h1 align="center">NextGenManager</h1>

<p align="center">
  <strong>Open-source Manufacturing ERP built for Indian MSMEs</strong>
</p>

<p align="center">
  <a href="#why-nextgenmanager">Why NextGenManager</a> &bull;
  <a href="#features">Features</a> &bull;
  <a href="#installation">Installation</a> &bull;
  <a href="#api-docs">API Docs</a> &bull;
  <a href="#roadmap">Roadmap</a> &bull;
  <a href="#contributing">Contributing</a>
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Java-17-orange?logo=openjdk&logoColor=white" alt="Java 17"/>
  <img src="https://img.shields.io/badge/Spring%20Boot-3.3.5-brightgreen?logo=springboot&logoColor=white" alt="Spring Boot"/>
  <img src="https://img.shields.io/badge/React-18.3-blue?logo=react&logoColor=white" alt="React 18"/>
  <img src="https://img.shields.io/badge/PostgreSQL-17-blue?logo=postgresql&logoColor=white" alt="PostgreSQL"/>
  <img src="https://img.shields.io/badge/License-Apache%202.0-blue" alt="Apache 2.0 License"/>
</p>

---

## Why NextGenManager?

India has over **6.3 crore MSMEs** that form the backbone of the manufacturing sector. Yet most small and mid-size manufacturers still run on Excel sheets, WhatsApp groups, and paper registers -- because existing ERP solutions are either too expensive or too complex to set up.

**NextGenManager is built specifically with Indian manufacturers in mind:**

- **GST & MSME compliant** -- Contact records support GSTIN, MSME registration numbers, and PAN out of the box
- **Job Work Challans** -- Built-in support for subcontracting workflows common in Indian manufacturing (challan-based job work)
- **Multi-address with GST** -- Manage multiple factory/godown addresses per vendor or customer, each with their own GSTIN
- **Make vs Buy Analysis** -- Helps small manufacturers decide whether to produce in-house or outsource -- a daily decision in Indian shop floors
- **Hindi-friendly architecture** -- Fully API-driven, ready for multi-language UI support
- **Runs on modest hardware** -- No need for expensive cloud infrastructure. Runs on a basic laptop or a Rs. 500/month VPS
- **Zero license cost** -- Free and open-source forever. No per-user fees, no hidden charges, no vendor lock-in

Whether you run a 10-person tool room in Pune, a 50-worker auto parts unit in Ludhiana, or a 200-person fabrication shop in Rajkot -- NextGenManager gives you the production planning, inventory control, and order management tools that until now were only available to large factories.

## Features

### Production & Manufacturing
- **Bill of Materials (BOM)** -- Multi-level BOMs with versioning, cost breakdown, where-used analysis, and ECO tracking
- **Work Orders** -- Create from BOM, track material issuance, operation progress, and state transitions (Created > Released > In Progress > Completed > Closed)
- **Routing & Operations** -- Define manufacturing processes with operation sequences, dependencies, setup/cycle times, and parallel operations
- **Production Scheduling** -- Schedule operations against work centers with capacity planning, shift management, and holiday calendars
- **Work Centers** -- Define machines and workstations with capacity, shift schedules, and availability tracking
- **Make vs Buy Analysis** -- Evaluate whether to manufacture in-house or purchase from vendors

### Inventory & Items
- **Item Master** -- Product catalog with material types (Raw, Semi-Finished, Finished), item codes, and auto-numbering
- **Vendor Pricing** -- Manage vendor-specific pricing history per item
- **Excel Import/Export** -- Bulk operations via Excel and CSV

> **Note:** Full inventory stock tracking (goods receipt, stock transfers, stock ledger) is currently under development. See [Roadmap](#roadmap).

### Sales & Marketing
- **Enquiries** -- Capture and track customer inquiries
- **Quotations** -- Generate and manage sales quotations
- **Sales Orders** -- Full sales order lifecycle with PDF generation
- **Job Work Challans** -- Subcontracting/job work management with challan tracking

### Contacts & Vendors
- **Unified Contacts** -- Manage vendors, customers, or dual-role contacts in one place
- **Multi-Address Support** -- Multiple factory/godown addresses per contact
- **GST & MSME Details** -- GSTIN, PAN, MSME registration number fields built-in

### Asset Management
- **Machine Registry** -- Track equipment details, specifications, and depreciation
- **Maintenance Events** -- Log machine events and maintenance activities
- **Production Logs** -- Monitor machine utilization and status history

### Security & Administration
- **JWT Authentication** -- Stateless auth with access/refresh token rotation
- **Role-Based Access Control** -- System roles (Super Admin, Admin, Production, Sales, Inventory) plus custom roles with module-level permissions
- **User Management** -- Create users, assign roles, manage status, audit login history

### Reporting & Documents
- **PDF Generation** -- BOMs, work orders, sales orders rendered as PDF
- **Excel Export** -- Export any data grid to Excel/CSV
- **Audit Trails** -- Change logs and history tracking on BOMs and work orders

### Developer Experience
- **Swagger/OpenAPI** -- Interactive API documentation at `/swagger-ui.html`
- **MCP Server** -- Built-in [Model Context Protocol](https://modelcontextprotocol.io/) server for AI/LLM integration
- **Flyway Migrations** -- Version-controlled database schema (73 migrations)
- **Docker Ready** -- One-command deployment with Docker Compose

## Tech Stack

| Layer | Technology |
|-------|-----------|
| **Backend** | Java 17, Spring Boot 3.3.5, Spring Security, Spring Data JPA |
| **Frontend** | React 18, Material-UI, Tailwind CSS, React Router v6 |
| **Database** | PostgreSQL 17 with Flyway migrations |
| **Auth** | JWT (HS512) via jjwt 0.12.6 |
| **File Storage** | MinIO (S3-compatible object storage) |
| **PDF Generation** | OpenHTMLtoPDF + Thymeleaf (server), jsPDF (client) |
| **Excel** | Apache POI (server), XLSX + PapaParse (client) |
| **Mapping** | MapStruct + Lombok |
| **API Docs** | springdoc-openapi 2.6.0 (Swagger UI) |
| **Build** | Maven 3.8+, npm |
| **Deploy** | Docker, Docker Compose |

---

## Installation

Complete guide to set up **both the backend and the frontend** on your machine.

### Prerequisites

| Software | Version | Download |
|----------|---------|----------|
| **Java JDK** | 17 or higher | [Download](https://adoptium.net/) |
| **Maven** | 3.8+ | [Download](https://maven.apache.org/download.cgi) (or use the included `./mvnw`) |
| **Node.js** | 18+ (includes npm) | [Download](https://nodejs.org/) |
| **PostgreSQL** | 15 or higher | [Download](https://www.postgresql.org/download/) |
| **MinIO** | Latest | [Download](https://min.io/download) |
| **Git** | Any recent version | [Download](https://git-scm.com/) |

### Step 1: Clone Both Repositories

```bash
# Backend
git clone https://github.com/siddhant2411/nextgenmanager.git

# Frontend
git clone https://github.com/siddhant2411/nextgenmanagerui.git
```

### Step 2: Set Up PostgreSQL

If PostgreSQL is already installed and running:

```bash
# Connect to PostgreSQL
psql -U postgres

# Create the database
CREATE DATABASE nextgenmanager;

# Verify it was created
\l

# Exit
\q
```

> **Windows users:** If `psql` is not in your PATH, find it at `C:\Program Files\PostgreSQL\17\bin\psql.exe` or use **pgAdmin** (GUI) to create the database.

### Step 3: Set Up MinIO (File Storage)

MinIO is used for storing file attachments (BOM documents, item images, etc.). It's a lightweight S3-compatible object storage server.

#### Option A: Run MinIO with Docker (Easiest)

```bash
docker run -d \
  --name minio \
  -p 9000:9000 \
  -p 9001:9001 \
  -e MINIO_ROOT_USER=minioadmin \
  -e MINIO_ROOT_PASSWORD=minioadmin \
  -v minio_data:/data \
  minio/minio server /data --console-address ":9001"
```

#### Option B: Run MinIO Binary (Without Docker)

**Windows:**
1. Download `minio.exe` from [https://min.io/download#/windows](https://min.io/download#/windows)
2. Open a terminal in the download folder and run:
```bash
set MINIO_ROOT_USER=minioadmin
set MINIO_ROOT_PASSWORD=minioadmin
minio.exe server D:\minio-data --console-address ":9001"
```

**Linux/macOS:**
```bash
wget https://dl.min.io/server/minio/release/linux-amd64/minio
chmod +x minio
MINIO_ROOT_USER=minioadmin MINIO_ROOT_PASSWORD=minioadmin ./minio server ~/minio-data --console-address ":9001"
```

#### Create the Storage Bucket

1. Open the MinIO Console at **http://localhost:9001**
2. Login with `minioadmin` / `minioadmin`
3. Go to **Buckets** > **Create Bucket**
4. Name it `nextgenmanager` and click **Create**

> MinIO API runs on port **9000** (used by the backend) and the web console on port **9001** (for you to manage files).

### Step 4: Configure the Backend

Create a file `src/main/resources/application-local.properties` inside the `nextgenmanager` folder:

```properties
# Database
spring.datasource.url=jdbc:postgresql://localhost:5432/nextgenmanager
spring.datasource.username=postgres
spring.datasource.password=your_postgres_password
spring.flyway.enabled=false
spring.jpa.hibernate.ddl-auto=update

# JWT Authentication
security.jwt.secret=change-this-to-a-random-64-character-string-for-your-security!!
security.jwt.algorithm=HS512
security.jwt.accessExpirationMillis=900000
security.jwt.refreshExpirationMillis=604800000
security.jwt.issuer=https://auth.erp.nextgenmanager.com
security.jwt.audience=erp-backend

# MinIO File Storage
minio.url=http://localhost:9000
minio.access-key=minioadmin
minio.secret-key=minioadmin
minio.bucket.name=nextgenmanager
minio.secure=false

# Frontend URL (for CORS)
frontend.url=http://localhost:3000
```

### Step 5: Start the Backend

```bash
cd nextgenmanager
./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

> **Windows:** Use `mvnw.cmd` instead of `./mvnw` if bash is not available.

Wait until you see `Started NextgenmanagerApplication` in the console. The API is now running at **http://localhost:8080**.

Verify by opening: **http://localhost:8080/swagger-ui.html**

### Step 6: Set Up and Start the Frontend

```bash
cd nextgenmanagerui

# Copy the example environment config
cp .env.example .env
```

The `.env` file should contain:
```env
REACT_APP_API_BASE_URL=http://localhost:8080/api
```

Install dependencies and start:
```bash
npm install
npm start
```

The UI will open at **http://localhost:3000**.

### Summary: What Should Be Running

| Service | URL | Purpose |
|---------|-----|---------|
| **PostgreSQL** | `localhost:5432` | Database |
| **MinIO API** | `localhost:9000` | File storage (backend connects here) |
| **MinIO Console** | `localhost:9001` | File management UI |
| **Backend API** | `localhost:8080` | Spring Boot REST API |
| **Swagger UI** | `localhost:8080/swagger-ui.html` | API documentation |
| **Frontend** | `localhost:3000` | React UI |

### Docker Compose (Alternative)

If you prefer to run everything with Docker:

```bash
cd nextgenmanager
```

Create a `.env` file:
```env
DATASOURCE_URL=jdbc:postgresql://postgres:5432/nextgenmanager
DATASOURCE_USERNAME=postgres
DATASOURCE_PASSWORD=your_secure_password

JWT_SECRET=change-this-to-a-random-64-character-string-for-your-security!!
JWT_ALGORITHM=HS512
JWT_ACCESS_EXPIRATION_MILLIS=900000
JWT_REFRESH_EXPIRATION_MILLIS=604800000
JWT_ISSUER=https://auth.erp.nextgenmanager.com
JWT_AUDIENCE=erp-backend

MINIO_URL=http://minio:9000
MINIO_ACCESS_KEY=minioadmin
MINIO_SECRET_KEY=minioadmin
MINIO_BUCKET_NAME=nextgenmanager

FRONTEND_URL=http://localhost:3000
```

```bash
docker-compose up --build
```

---

## API Docs

Once the backend is running, interactive API documentation is available at:

- **Swagger UI:** `http://localhost:8080/swagger-ui.html`
- **OpenAPI JSON:** `http://localhost:8080/v3/api-docs`

### Key API Endpoints

| Module | Endpoint | Description |
|--------|----------|-------------|
| **Auth** | `POST /api/auth/login` | Login and get JWT tokens |
| **Auth** | `POST /api/auth/refresh` | Refresh access token |
| **Users** | `GET /api/auth/users` | List all users |
| **Roles** | `POST /api/auth/roles` | Create custom role |
| **BOM** | `GET /api/bom/all` | List bills of materials |
| **BOM** | `POST /api/bom` | Create a BOM |
| **BOM** | `GET /api/bom/{id}/cost-breakdown` | Cost analysis |
| **BOM** | `GET /api/bom/where-used/{itemId}` | Where-used analysis |
| **Work Orders** | `POST /api/production/work-order` | Create work order |
| **Work Orders** | `POST /api/production/work-order/{id}/schedule` | Schedule operations |
| **Inventory** | `GET /api/inventory_item/all` | List inventory items |
| **Inventory** | `POST /api/inventory_item/filter` | Filter/search items |
| **Contacts** | `GET /api/contact` | Search contacts |
| **Sales** | `POST /api/sales-orders` | Create sales order |
| **Sales** | `GET /api/sales-orders/{id}/pdf` | Generate PDF |
| **Routing** | `POST /api/production/routing` | Define manufacturing routing |
| **Machines** | `GET /api/assets/machines` | List machines |
| **Marketing** | `GET /api/marketing/enquiry` | List enquiries |

All endpoints (except login and refresh) require a valid JWT Bearer token.

## Project Structure

```
nextgenmanager/                    (Backend - this repo)
├── src/main/java/com/nextgenmanager/nextgenmanager/
│   ├── common/                    # Auth, security, file management, base entities
│   ├── bom/                       # Bill of Materials module
│   ├── production/                # Work orders, routing, scheduling, work centers
│   ├── items/                     # Inventory items, item codes, numbering
│   ├── Inventory/                 # Stock instances and tracking
│   ├── contact/                   # Vendors, customers, addresses
│   ├── sales/                     # Sales orders
│   ├── marketing/                 # Enquiries, quotations
│   ├── purchase/                  # Purchase orders
│   ├── assets/                    # Machine details, events, logs
│   ├── employee/                  # Employee management
│   ├── component/                 # Component/part management
│   ├── mcp/                       # Model Context Protocol server
│   └── config/                    # Spring configuration
├── src/main/resources/
│   ├── db/migration/              # 73 Flyway SQL migrations
│   ├── templates/                 # Thymeleaf templates (PDF generation)
│   └── application.properties
├── pom.xml
├── Dockerfile
└── docker-compose.yml

nextgenmanagerui/                  (Frontend - separate repo)
├── src/
│   ├── auth/                      # JWT authentication context & guards
│   ├── services/                  # API service layer (Axios)
│   ├── pages/                     # 23 route-level page components
│   ├── components/                # Reusable UI components by domain
│   ├── config/                    # Environment configuration
│   └── utils/                     # Helpers and formatters
└── package.json
```

Each backend module follows: **Model > Repository > Service (Interface + Impl) > Controller > DTO > Mapper**

## Environment Variables

| Variable | Description | Default |
|----------|-------------|---------|
| `DATASOURCE_URL` | PostgreSQL JDBC URL | -- |
| `DATASOURCE_USERNAME` | Database username | -- |
| `DATASOURCE_PASSWORD` | Database password | -- |
| `JWT_SECRET` | JWT signing key (64+ chars) | -- |
| `JWT_ALGORITHM` | JWT algorithm | `HS512` |
| `JWT_ACCESS_EXPIRATION_MILLIS` | Access token TTL (ms) | `900000` (15 min) |
| `JWT_REFRESH_EXPIRATION_MILLIS` | Refresh token TTL (ms) | `604800000` (7 days) |
| `JWT_ISSUER` | JWT issuer claim | -- |
| `JWT_AUDIENCE` | JWT audience claim | -- |
| `MINIO_URL` | MinIO/S3 endpoint | -- |
| `MINIO_ACCESS_KEY` | MinIO access key | -- |
| `MINIO_SECRET_KEY` | MinIO secret key | -- |
| `MINIO_BUCKET_NAME` | Storage bucket name | -- |
| `FRONTEND_URL` | Frontend origin (for CORS) | -- |
| `MCP_SERVER_ENABLED` | Enable MCP server | `true` |

## Roadmap

### In Progress
- [ ] **Inventory Integration** -- Full stock tracking with goods receipt, goods issue, stock transfers, and stock ledger
- [ ] Purchase Order module completion

### Planned
- [ ] GST invoice generation (GSTR-1 compatible)
- [ ] Quality Control (QC) inspection workflows
- [ ] Dashboard analytics and KPIs
- [ ] Multi-warehouse/godown inventory support
- [ ] Barcode/QR code scanning for shop floor
- [ ] Email/SMS notifications and alerts
- [ ] Report builder with custom templates
- [ ] Multi-currency support for export orders
- [ ] Mobile-responsive PWA for shop floor use
- [ ] Hindi and regional language UI support
- [ ] E-way bill integration
- [ ] Tally/Busy accounting integration

## Who Is This For?

- **Small manufacturers** (10-200 employees) looking to move from Excel to a real ERP
- **Job shops and tool rooms** that manage multiple customer orders with different BOMs
- **Auto parts, engineering, and fabrication units** across India's MSME clusters
- **IT teams** building custom ERP solutions for manufacturing clients
- **Students and developers** learning full-stack development with a real-world project

## Support & Contact

**Need help setting up?** Have questions or want to discuss a use case?

- **Email:** siddhantmavani1@gmail.com
- **GitHub Issues:** [Open an issue](https://github.com/siddhant2411/nextgenmanager/issues) for bugs or feature requests

### Commercial Support

NextGenManager is free and open-source, and always will be. However, if you need:

- **Dedicated setup and deployment** for your factory
- **Custom module development** tailored to your manufacturing process
- **Priority bug fixes and feature requests**
- **Training and onboarding** for your team
- **Hosted/managed version** so you don't have to maintain servers

Reach out at **siddhantmavani1@gmail.com** for commercial support plans.

## Contributing

Contributions are welcome! Please see [CONTRIBUTING.md](CONTRIBUTING.md) for guidelines.

Whether it's a bug fix, new feature, or documentation improvement -- all contributions help. If you're new to open source, look for issues labeled `good first issue`.

**Frontend repo:** [siddhant2411/nextgenmanagerui](https://github.com/siddhant2411/nextgenmanagerui)

## License

This project is licensed under the Apache License 2.0. See the [LICENSE](LICENSE) file for details.

---

<p align="center">
  <sub>Built in India, for Indian manufacturers -- and for manufacturers everywhere.</sub>
</p>

<p align="center">
  <a href="https://github.com/siddhant2411/nextgenmanager/stargazers">Star this repo</a> if you find it useful!
</p>
