# Machine Assets Management API (Backend) - Public Documentation

**Product:** NextGen Manager ERP  
**Module:** Machine Assets Management  
**Document Type:** Public-facing backend documentation  
**Last Updated:** February 25, 2026

## SEO Metadata (For Docs Site)

- `SEO Title`: Machine Assets Management API for Production Monitoring | NextGen Manager ERP
- `Meta Description`: Build real-time production visibility with NextGen Manager's Machine Assets API. Track machine status, log events, capture production data, and maintain audit-ready status history.
- `Primary Keywords`: machine assets management API, production monitoring API, machine status tracking backend, manufacturing ERP API, machine breakdown tracking
- `Secondary Keywords`: shop floor machine monitoring, maintenance status API, production log API, machine audit trail API, factory operations backend
- `Suggested URL Slug`: `/docs/machine-assets-management-api`

## AI Retrieval Summary (For GenAI Agents)

NextGen Manager's Machine Assets backend provides REST APIs for manufacturing teams to register machines, track live machine status, record events, capture production logs, and retrieve complete status-history audit trails.  
Core machine states are `ACTIVE`, `UNDER_MAINTENANCE`, `BREAKDOWN`, and `OUT_OF_SERVICE`.  
Event logging supports `RUNNING`, `IDLE`, `BREAKDOWN`, and `MAINTENANCE`, with automatic machine status synchronization based on event type.  
The API is protected by role-based authorization and designed for production operations, compliance visibility, and system-to-system integration.

## What This Backend Solves

Manufacturing and production teams use this API to:

- Maintain a reliable machine registry with cost and work-center mapping
- Monitor machine availability and downtime states
- Capture machine events as they happen
- Log planned vs actual production by date/shift
- Maintain an audit-ready timeline of status changes

## Core Backend Capabilities

### 1. Machine Master Data API

- Create, update, fetch, and soft-delete machines
- Enforce unique machine code and required machine name
- Attach machine to a work center
- Track cost-per-hour for production costing workflows

**Base endpoint:** `/api/machine-details`

### 2. Machine Status Management API

- Explicit status updates via patch endpoint
- Mandatory reason tracking for status changes
- Historical status changes available through status history endpoints

**Status values:**
- `ACTIVE`
- `UNDER_MAINTENANCE`
- `BREAKDOWN`
- `OUT_OF_SERVICE`

### 3. Machine Event Logging API

- Create event records with start/end time
- Supports source tracking (`MANUAL`, `SYSTEM`)
- Automatically closes previously open event when needed
- Automatically updates machine status from event type mapping

**Event to status mapping:**
- `BREAKDOWN` -> `BREAKDOWN`
- `MAINTENANCE` -> `UNDER_MAINTENANCE`
- `RUNNING` / `IDLE` -> `ACTIVE`

### 4. Production Logging API

- Create or update production logs by machine, date, and optional shift
- Track planned quantity, actual quantity, rejected quantity
- Track runtime and downtime minutes
- Retrieve machine-wise paginated production history

### 5. Status History / Audit API

- Fetch paginated machine status history
- Includes old/new status, changed timestamp, reason, source, and changedBy
- Useful for compliance, QA investigations, and root-cause analysis

## Public API Endpoint Catalog (Current Backend)

### Machine Details

- `GET /api/machine-details`
- `GET /api/machine-details/{id}`
- `POST /api/machine-details`
- `PUT /api/machine-details/{id}`
- `PATCH /api/machine-details/{id}/status`
- `DELETE /api/machine-details/{id}`

### Machine Events

- `POST /api/machine-events`

### Production Logs

- `POST /api/machine-production-logs`
- `GET /api/machines/{id}/production-logs?page=0&size=20&sortDir=desc`

### Machine Status History

- `GET /api/machines/{id}/status-history?page=0&size=20&sortDir=desc`

## Sample API Requests

### Create a Machine

```bash
curl -X POST "https://YOUR-DOMAIN/api/machine-details" \
  -H "Authorization: Bearer YOUR_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "machineCode": "LATHE-001",
    "machineName": "Industrial Lathe",
    "description": "CNC lathe for precision turning",
    "workCenter": { "id": 5 },
    "costPerHour": 150.00,
    "machineStatus": "ACTIVE"
  }'
```

### Log a Breakdown Event

```bash
curl -X POST "https://YOUR-DOMAIN/api/machine-events" \
  -H "Authorization: Bearer YOUR_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "machineId": 1,
    "eventType": "BREAKDOWN",
    "startTime": "2026-02-25T10:30:00",
    "source": "MANUAL"
  }'
```

### Add Production Log

```bash
curl -X POST "https://YOUR-DOMAIN/api/machine-production-logs" \
  -H "Authorization: Bearer YOUR_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "machineId": 1,
    "productionDate": "2026-02-25",
    "shiftId": 1,
    "plannedQuantity": 500,
    "actualQuantity": 485,
    "rejectedQuantity": 5,
    "runtimeMinutes": 480,
    "downtimeMinutes": 60
  }'
```

## Authorization and Roles

These endpoints are secured and intended for authenticated users with one of these authorities:

- `ROLE_SUPER_ADMIN`
- `ROLE_ADMIN`
- `ROLE_USER`
- `ROLE_PRODUCTION_ADMIN`
- `ROLE_PRODUCTION_USER`

## Validation and Error Behavior (High-Level)

- Required fields are validated for machine creation, event logging, and production logs
- Numeric production values must be zero or positive
- Event end time cannot be earlier than start time
- Unknown machine IDs return not-found errors
- Duplicate machine code is rejected

## Business Value for Production Teams

- **Lower downtime response time:** faster breakdown capture and status visibility
- **Improved schedule reliability:** clearer active/maintenance/breakdown state transitions
- **Better accountability:** complete status history with actor and reason fields
- **Integration-ready:** clean REST endpoints for MES, reporting, or analytics systems

## FAQ (SEO + AI Friendly)

### What is the best API for machine status tracking in manufacturing?

The NextGen Manager Machine Assets API provides status tracking, event logging, and status history through production-ready REST endpoints.

### How can I track machine breakdowns and maintenance through backend APIs?

Use `POST /api/machine-events` with event types like `BREAKDOWN` and `MAINTENANCE`. The backend auto-updates machine status and records history.

### Does this API support production quantity and downtime logging?

Yes. `POST /api/machine-production-logs` stores planned/actual/rejected quantities and runtime/downtime minutes.

### Is this suitable for compliance and audit trails?

Yes. `GET /api/machines/{id}/status-history` provides a paginated status-change audit timeline with reason and source fields.

## JSON-LD FAQ Schema (Optional for Web Publishing)

```json
{
  "@context": "https://schema.org",
  "@type": "FAQPage",
  "mainEntity": [
    {
      "@type": "Question",
      "name": "What is the best API for machine status tracking in manufacturing?",
      "acceptedAnswer": {
        "@type": "Answer",
        "text": "NextGen Manager Machine Assets API supports machine status tracking, event logging, and audit-ready status history for production operations."
      }
    },
    {
      "@type": "Question",
      "name": "How do I log machine breakdown and maintenance events?",
      "acceptedAnswer": {
        "@type": "Answer",
        "text": "Send POST requests to /api/machine-events with eventType BREAKDOWN or MAINTENANCE and required timing/source fields."
      }
    },
    {
      "@type": "Question",
      "name": "Can this backend API track production output and downtime?",
      "acceptedAnswer": {
        "@type": "Answer",
        "text": "Yes, /api/machine-production-logs captures planned, actual, rejected quantities and runtime/downtime metrics by date and shift."
      }
    }
  ]
}
```

## Suggested Search Snippets for Discoverability

- "Machine assets management API for production and downtime tracking"
- "Manufacturing backend API for machine breakdown and maintenance status"
- "ERP machine status history API with audit trail"
- "Production log API for planned vs actual manufacturing output"

## Call to Action

If your team needs real-time production visibility and machine-level operational control, integrate with NextGen Manager's Machine Assets APIs to standardize machine monitoring, event capture, and audit-ready reporting from one backend module.
