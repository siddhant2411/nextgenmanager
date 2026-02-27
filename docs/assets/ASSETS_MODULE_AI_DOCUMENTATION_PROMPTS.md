# Assets Module - AI Prompt for Product Documentation Generation

**Document Version:** 1.0  
**Purpose:** Ready-to-use prompt template for AI agents to generate public-facing product documentation  
**Created:** February 25, 2026

---

## How to Use This Document

This document contains **production-ready prompts** designed for use with AI language models (GPT-4, Claude 3, Gemini Pro, etc.) to automatically generate professional, comprehensive product documentation for the Machine Assets Management Module.

### Quick Start
1. Copy the relevant prompt from sections below
2. Paste into your AI assistant (ChatGPT, Claude, etc.)
3. AI will generate formatted, ready-to-publish documentation
4. Review and customize for your specific needs
5. Publish to documentation portal

---

## Prompt Set 1: User-Facing Product Documentation

### Prompt Template: Executive Overview Document

```
TASK: Generate executive-level product overview for Machine Assets Management module

CONTEXT:
- Product: NextGen Manager ERP - Machine Assets Management Module
- Target Audience: Production managers, facility supervisors, operations directors
- Tone: Professional, clear, non-technical
- Format: Markdown with clear sections and examples

INCLUDE THESE SECTIONS:

1. What is Machine Assets Management?
   - 3-4 sentence explanation of the module's purpose
   - Key business value and benefits
   - Real-world use case example

2. Key Capabilities
   - Machine Registration & Configuration
   - Real-time Status Monitoring with 4-state model (Active, Under Maintenance, Breakdown, Out of Service)
   - Automated Event Logging and Status Updates
   - Production Efficiency Tracking (planned vs actual, rejection rates)
   - Complete Audit Trail for compliance

3. Core Features Explained (user-friendly language)
   - Machine Registration: Adding new machinery to system with cost and work center assignment
   - Status Management: Manual and automatic status changes with reason tracking
   - Event Logging: Recording machine events (running, idle, breakdown, maintenance)
   - Production Metrics: Daily/shift-level production data logging and analysis
   - History & Audit: Complete record of all changes with who, what, when, why

4. Business Benefits
   - Improved Production Visibility: Real-time machine status across facility
   - Better Resource Planning: Cost-per-hour tracking for accurate costing
   - Compliance Ready: Complete audit trail for quality management
   - Data-Driven Decisions: Production efficiency metrics for optimization
   - Automated Efficiency: Auto-status updates reduce manual work

5. Who Can Use This
   - Production Managers: Plan production, monitor efficiency
   - Supervisors: Check machine status, log events
   - Quality Managers: Review production logs and efficiency
   - Finance: Track machine costs
   - Management: Dashboard view of facility utilization

FORMATTING:
- Use ### for section headers
- Use bullet points for lists
- Add 2-3 example scenarios
- Include simple ASCII diagrams where helpful
- Keep language simple and jargon-free
```

### Prompt Template: Getting Started Guide

```
TASK: Create Getting Started guide for new users of Machine Assets module

TARGET AUDIENCE: Production staff, supervisors, new employees

DOCUMENT STRUCTURE:

1. System Access
   - How to log in to the application
   - Finding the Assets module in the menu
   - Understanding user roles and permissions

2. Your First Machine Registration (Step-by-step tutorial)
   Step 1: Navigate to Machine Details section
   Step 2: Click "Create New Machine"
   Step 3: Fill in machine details:
     - Machine Code (e.g., "LATHE-001") - unique identifier
     - Machine Name (e.g., "Industrial Lathe Model X")
     - Description (optional details about the machine)
     - Work Center (select which department operates it)
     - Cost Per Hour (operating cost for costing)
     - Status (initially set to Active)
   Step 4: Review and Save

3. Logging a Machine Event
   When to Log Events: Whenever machine status changes
   Steps:
     1. Go to Machine Details page
     2. Select the machine
     3. Click "Log Event"
     4. Select event type:
        - RUNNING: Machine actively producing
        - IDLE: Machine available but not running
        - BREAKDOWN: Unexpected failure
        - MAINTENANCE: Scheduled maintenance
     5. Set start time (when event occurred)
     6. Set end time (when event ended, if known)
     7. Choose source: Manual (you entering it) or System (automatic)
     8. Save

4. Recording Production Data
   What to Log: Daily production results for each machine
   Steps:
     1. Go to Production Logs section
     2. Select machine and production date
     3. Enter:
        - Planned Quantity: Target production for the day
        - Actual Quantity: What was actually produced
        - Rejected Quantity: Units that didn't meet quality
        - Runtime Minutes: How long machine ran
        - Downtime Minutes: How long machine was idle/broken
     4. System automatically calculates efficiency percentage
     5. Save

5. Checking Machine Status
   - View current status of any machine
   - See last status change time
   - See the reason for current status
   - View complete history of all status changes

6. Understanding Status History
   - View all changes to a machine's status
   - See who made the change and when
   - Understand the reason for each change
   - Audit trail for compliance

7. Common Tasks
   Task: Change a machine from "Active" to "Under Maintenance"
   - Go to machine details
   - Click "Change Status"
   - Select "Under Maintenance"
   - Enter reason (e.g., "Scheduled belt replacement")
   - System records who made change and when

   Task: View machine efficiency for the week
   - Go to Production Logs
   - Select machine
   - Filter by date range
   - Review efficiency percentage for each day
   - Identify trends

   Task: Find all machines that had breakdowns this month
   - Go to Status History
   - Filter by status change to "Breakdown"
   - Filter by date range (this month)
   - Review details of each breakdown

8. Tips & Best Practices
   - Log events as soon as they occur (don't wait until end of day)
   - Be specific in reasons for status changes (helps troubleshooting)
   - Update production data by end of shift (for accurate tracking)
   - Use "Maintenance" status only for scheduled maintenance (not for breakdowns)
   - Review daily efficiency reports to identify problem machines

TONE: Friendly, supportive, encouraging
EXAMPLES: Include specific examples with realistic machine names and times
```

---

## Prompt Set 2: Technical/Administrator Documentation

### Prompt Template: System Administration Guide

```
TASK: Create System Administration and Configuration Guide

TARGET AUDIENCE: System administrators, IT support staff, database administrators

DOCUMENT SHOULD INCLUDE:

1. System Architecture Overview
   - Describe how the Machine Assets module fits into NextGen Manager ERP
   - Integration with Work Centers module
   - Integration with Production Scheduling
   - Data flow and relationships

2. Database Schema
   - Tables: MachineDetails, MachineEvent, MachineProductionLog, MachineStatusHistory
   - For each table, provide:
     - Purpose
     - Key columns and data types
     - Indexes created
     - Relationships to other tables
   - Include sample ER diagram

3. Installation & Setup
   - Prerequisite components
   - Database setup steps
   - Configuration properties
   - Initial data seeding (if needed)
   - Verification steps

4. Configuration Parameters
   ```
   Property                        Value       Purpose
   machine.event.timeout           1440        Minutes before open event auto-closes
   machine.query.cache.ttl         300         Cache duration in seconds
   machine.audit.retention.days    2555        Days to keep audit records (7 years)
   ```

5. User Roles & Permissions
   Table:
   - ROLE_SUPER_ADMIN: Full access to all operations
   - ROLE_ADMIN: Can create/update/delete machines, view all reports
   - ROLE_PRODUCTION_ADMIN: Can manage production logging, status changes
   - ROLE_PRODUCTION_USER: Can log events and production data, no delete
   - ROLE_USER: Read-only access

6. Database Maintenance
   - Backup strategy: Daily incremental, weekly full
   - Index maintenance: Monthly ANALYZE command
   - Archive strategy: Move logs >1 year to archive table
   - Cleanup procedures: Remove orphaned records

7. Performance Tuning
   - Connection pool settings for optimal performance
   - Query optimization techniques
   - Index recommendations for large datasets
   - Caching strategy implementation

8. Monitoring & Alerts
   - Key metrics to monitor:
     - Average query response time (<100ms)
     - Database connection pool utilization (<80%)
     - Open event count (should be cleaned up)
     - Storage growth rate
   - Alert thresholds and actions

9. Troubleshooting
   - Common errors and solutions
   - Log file locations and analysis
   - Performance diagnosis
   - Data consistency checks

10. Disaster Recovery
    - Backup and restore procedures
    - Data loss prevention
    - Recovery time objectives (RTO)
    - Recovery point objectives (RPO)

TECHNICAL DEPTH: Medium (assumes IT/technical background)
INCLUDE: Configuration examples, SQL scripts, error messages
```

### Prompt Template: API Developer Documentation

```
TASK: Create REST API Documentation for Developers

TARGET AUDIENCE: Backend developers, API integration specialists, system integrators

DOCUMENT STRUCTURE:

1. API Overview
   - Base URL: /api
   - Authentication: OAuth2 Bearer Token
   - Response Format: JSON
   - API Version: 1.0
   - Rate Limits: 1000 requests per hour per user

2. Authentication
   - Include Authorization header: "Authorization: Bearer YOUR_TOKEN"
   - Token obtained from /auth/token endpoint
   - Token expires after 24 hours

3. Machine Details Endpoints

   GET /machine-details
   Description: Retrieve all active machines with pagination
   Query Parameters:
   - page (integer, default: 0) - Zero-based page number
   - size (integer, default: 20) - Items per page, max 100
   - sortBy (string, default: "id") - Field to sort by
   - sortDir (string, "asc"/"desc") - Sort direction
   
   Response Headers:
   - X-Total-Count: Total records matching filter
   - X-Total-Pages: Total pages available
   
   Success Response (200):
   ```json
   {
     "content": [
       {
         "id": 1,
         "machineCode": "LATHE-001",
         "machineName": "Industrial Lathe",
         "costPerHour": 150.50,
         "machineStatus": "ACTIVE",
         "workCenter": {
           "id": 5,
           "name": "Assembly Line A"
         }
       }
     ],
     "pageable": {
       "pageNumber": 0,
       "pageSize": 20,
       "totalElements": 125,
       "totalPages": 7
     }
   }
   ```

   GET /machine-details/{id}
   Description: Get specific machine by ID
   Path Parameters:
   - id (integer) - Machine ID
   
   Success Response (200): Single machine object as above
   Error Response (404): {"error": "Machine not found", "id": 999}

   POST /machine-details
   Description: Create new machine
   Required Headers: Content-Type: application/json
   
   Request Body:
   ```json
   {
     "machineCode": "MILL-001",
     "machineName": "CNC Milling Machine",
     "description": "5-axis precision milling",
     "workCenterId": 5,
     "costPerHour": 250.00,
     "machineStatus": "ACTIVE"
   }
   ```
   
   Validation Rules:
   - machineCode: Required, unique, max 50 chars
   - machineName: Required, max 100 chars
   - workCenterId: Required, must exist in WorkCenter table
   - costPerHour: Required, 0 to 999999.99
   
   Success Response (201): Created machine object with ID
   Validation Error (400): 
   ```json
   {
     "errors": [
       {
         "field": "machineCode",
         "message": "Machine code already exists",
         "rejectedValue": "MILL-001"
       }
     ]
   }
   ```

   PUT /machine-details/{id}
   Description: Update entire machine record
   Request Body: Full machine object
   Success Response (200): Updated machine

   PATCH /machine-details/{id}/status
   Description: Change machine status
   Request Body:
   ```json
   {
     "newStatus": "UNDER_MAINTENANCE",
     "reason": "Scheduled belt replacement"
   }
   ```
   Success Response (200): Updated machine with new status

   DELETE /machine-details/{id}
   Description: Soft delete (deactivate) machine
   Success Response (200): {"message": "Machine deleted", "id": 1}

4. Machine Events Endpoints

   POST /machine-events
   Description: Log a machine event (status change)
   Request Body:
   ```json
   {
     "machineId": 1,
     "eventType": "BREAKDOWN",
     "startTime": "2026-02-25T10:30:00Z",
     "endTime": "2026-02-25T11:45:00Z",
     "source": "MANUAL"
   }
   ```
   Validation:
   - All fields required except endTime
   - eventType: RUNNING, IDLE, BREAKDOWN, MAINTENANCE
   - source: MANUAL, SYSTEM
   - startTime format: ISO 8601
   - endTime cannot be before startTime
   
   Success Response (201): Created event with ID and duration

   [Add similar documentation for GET endpoints that should exist]

5. Production Logs Endpoints

   POST /machine-production-logs
   POST /api/machine-production-logs
   Description: Create or update production log
   Request Body:
   ```json
   {
     "machineId": 1,
     "productionDate": "2026-02-25",
     "shiftId": 1,
     "plannedQuantity": 500,
     "actualQuantity": 485,
     "rejectedQuantity": 5,
     "runtimeMinutes": 480,
     "downtimeMinutes": 60
   }
   ```
   Validation: All quantities must be >= 0
   Success Response (201): Created log with calculated efficiency

   GET /machines/{id}/production-logs
   Description: Retrieve production logs for a machine
   Query Params: page, size, sortDir
   Success Response (200): Paginated list of logs

6. Status History Endpoints

   GET /machines/{id}/status-history
   Description: Get audit trail of status changes
   Success Response (200): List of status change records

7. HTTP Status Codes
   - 200 OK: Successful GET/PUT/PATCH
   - 201 Created: Successful POST
   - 400 Bad Request: Validation error
   - 401 Unauthorized: Missing/invalid token
   - 403 Forbidden: Insufficient permissions
   - 404 Not Found: Resource doesn't exist
   - 409 Conflict: Duplicate data (e.g., duplicate machine code)
   - 500 Server Error: Unexpected error

8. Error Response Format
   ```json
   {
     "timestamp": "2026-02-25T12:00:00Z",
     "status": 400,
     "error": "Validation Error",
     "message": "Input validation failed",
     "path": "/api/machine-details",
     "details": {
       "errors": [
         {
           "field": "costPerHour",
           "message": "must be less than or equal to 999999.99",
           "rejectedValue": 1000000.00
         }
       ]
     }
   }
   ```

9. Code Examples

   Example: Create Machine (Java)
   ```java
   RestTemplate restTemplate = new RestTemplate();
   HttpHeaders headers = new HttpHeaders();
   headers.set("Authorization", "Bearer " + token);
   headers.setContentType(MediaType.APPLICATION_JSON);
   
   MachineCreateRequest request = new MachineCreateRequest();
   request.setMachineCode("LATHE-001");
   request.setMachineName("Industrial Lathe");
   request.setWorkCenterId(5);
   request.setCostPerHour(150.50);
   
   HttpEntity<MachineCreateRequest> entity = 
       new HttpEntity<>(request, headers);
   
   ResponseEntity<MachineDTO> response = restTemplate.exchange(
       "http://localhost:8080/api/machine-details",
       HttpMethod.POST,
       entity,
       MachineDTO.class
   );
   ```

   Example: Create Machine (cURL)
   ```bash
   curl -X POST http://localhost:8080/api/machine-details \
     -H "Authorization: Bearer YOUR_TOKEN" \
     -H "Content-Type: application/json" \
     -d '{
       "machineCode": "LATHE-001",
       "machineName": "Industrial Lathe",
       "workCenterId": 5,
       "costPerHour": 150.50
     }'
   ```

   Example: Query Production Data (Python)
   ```python
   import requests
   
   headers = {"Authorization": f"Bearer {token}"}
   response = requests.get(
       "http://localhost:8080/api/machines/1/production-logs",
       headers=headers,
       params={"page": 0, "size": 20}
   )
   logs = response.json()["content"]
   ```

10. Rate Limiting
    - Limit: 1000 requests/hour
    - Headers returned:
      - X-RateLimit-Limit: 1000
      - X-RateLimit-Remaining: 999
      - X-RateLimit-Reset: Unix timestamp
    - When exceeded (429): Wait until X-RateLimit-Reset

11. Versioning Strategy
    - API version in URL: /api/v1/
    - Current version: 1.0
    - Backward compatibility maintained
    - Deprecation notice given 6 months before removal

12. Pagination Best Practices
    - Default page size: 20
    - Maximum page size: 100
    - Recommended size: 50 for most use cases
    - Always provide totalElements in response

TECHNICAL LEVEL: High (assumes developer experience)
INCLUDE: Real request/response examples, error scenarios, code samples
```

---

## Prompt Set 3: Operational & Compliance Documentation

### Prompt Template: Business Process & Workflows

```
TASK: Document business processes and workflows for Machine Assets Management

TARGET AUDIENCE: Process managers, business analysts, compliance officers, auditors

INCLUDE:

1. Machine Lifecycle Process
   
   Phase 1: Acquisition & Setup
   - Approval of new machine purchase
   - Machine received and physically set up
   - Machine registered in system with code, name, specs
   - Assigned to work center
   - Cost per hour established for costing
   - Initial status: ACTIVE
   - Who: Procurement, Operations, IT
   - Timeline: Day of installation

   Phase 2: Operational Status
   - Machine runs under normal conditions
   - Status: ACTIVE
   - Events logged: RUNNING, IDLE
   - Production logs recorded daily
   - Efficiency tracked and monitored
   - Duration: Entire operational life

   Phase 3: Maintenance Cycle
   - Scheduled maintenance planned
   - Status changed to: UNDER_MAINTENANCE
   - Reason recorded: e.g., "Belt replacement scheduled for 2026-02-26"
   - Machine not available for production
   - Maintenance performed (external to system)
   - Upon completion, status changed back to: ACTIVE
   - Reason recorded: "Maintenance completed successfully"
   - Duration: Hours to days

   Phase 4: Breakdown Handling
   - Unexpected failure occurs
   - BREAKDOWN event logged immediately
   - Status automatically changes to: BREAKDOWN
   - Production stops
   - Diagnosis and repair (external)
   - BREAKDOWN event closed with end time
   - Status changed back to: ACTIVE
   - Reason recorded: "Hydraulic leak repaired"
   - Timeline: Minutes to days depending on issue

   Phase 5: End of Life
   - Machine becomes obsolete/damaged beyond repair
   - Status changed to: OUT_OF_SERVICE
   - Reason recorded: "Equipment end of life - 10+ years old"
   - Machine may be archived or removed
   - Historical data retained for compliance

2. Event Logging Workflow
   
   Trigger: Any significant machine status change
   
   Steps:
   1. Employee observes machine event (breakdown, maintenance start, etc.)
   2. Employee logs event in system within reasonable time
   3. System records:
      - Event type (RUNNING/IDLE/BREAKDOWN/MAINTENANCE)
      - Start time
      - End time (if known)
      - Source (MANUAL or SYSTEM)
   4. System auto-closes any previous open event
   5. System automatically updates machine status
   6. Status history records the change
   7. Report available for analysis
   
   Owner: Operators, Supervisors
   Frequency: As needed throughout shift
   Timing: Real-time or within 1 hour of occurrence

3. Production Logging Workflow
   
   Trigger: End of production shift
   
   Steps:
   1. Production supervisor collects day's data:
      - Planned quantity for the day
      - Actual quantity produced
      - Quantity rejected (quality issues)
      - Machine runtime (hours machine was running)
      - Downtime (hours machine was not running)
   2. Supervisor logs data in system before shift end
   3. System validates all quantities >= 0
   4. System calculates:
      - Efficiency %: (Actual / Planned) × 100
      - Rejection Rate %: (Rejected / Actual) × 100
   5. Data stored for reporting and analysis
   6. Reports available for management review
   
   Owner: Production Supervisor
   Frequency: Daily, end of shift
   Timing: Within 30 minutes of shift end

4. Status Change Workflow
   
   Manual Status Change:
   1. Need identified (maintenance needed, machine down, etc.)
   2. Authorized person goes to machine in system
   3. Clicks "Change Status"
   4. Selects new status
   5. Enters reason (mandatory)
   6. System records:
      - Old status → New status
      - Timestamp
      - Who made the change
      - Reason provided
   7. Change reflected in reports immediately
   
   Automatic Status Change:
   1. Event logged in system
   2. System maps event type to status:
      - BREAKDOWN → BREAKDOWN status
      - MAINTENANCE → UNDER_MAINTENANCE status
      - RUNNING/IDLE → ACTIVE status
   3. Status updated automatically
   4. Status history recorded with reason "Auto-updated from machine event"
   
   Owner: Operators (manual), System (automatic)
   Frequency: As needed
   Impact: Real-time change

5. Reporting & Analysis Workflow
   
   Daily Reports:
   - List all active machines with current status
   - Production efficiency for each machine (% of planned achieved)
   - Rejection rates by machine
   - Downtime summary
   - Owner: Production Manager
   - Frequency: Daily, morning review

   Weekly Reports:
   - Machine efficiency trends
   - Breakdown frequency and duration
   - Maintenance effectiveness
   - Cost per unit produced
   - Owner: Operations Manager
   - Frequency: Weekly

   Monthly Reports:
   - Overall facility efficiency
   - Major incidents (breakdowns >2 hours)
   - Maintenance schedule compliance
   - Budget vs. actual machine costs
   - Owner: Director/Management
   - Frequency: Monthly

   Compliance Audit Reports:
   - Complete audit trail of all machine changes
   - Who made changes, when, and why
   - Trend analysis of machine reliability
   - Owner: Quality/Compliance Officer
   - Frequency: Quarterly, as needed

---

## Prompt Set 4: FAQ & Troubleshooting Documentation

### Prompt Template: Frequently Asked Questions

```
TASK: Create FAQ document for Machine Assets Management module

FORMAT: Question and Answer pairs, organized by category

CATEGORIES:

GENERAL QUESTIONS
Q: What is the purpose of the Machine Assets module?
A: The Machine Assets module tracks and manages all production machinery, their status, maintenance, and production efficiency. It helps you know where machines are, what they're doing, and how efficiently they're operating.

Q: Who should use this module?
A: Production supervisors, operators, maintenance staff, and production managers. Machine Assets provides visibility for everyone involved in production operations.

Q: What information does it track?
A: Machine identification (code, name), location (work center), operating costs, current status, and historical record of all status changes and production events.

GETTING STARTED
Q: How do I add a new machine to the system?
A: Go to Machine Details section, click "Create New Machine," fill in the machine code, name, work center, and cost per hour, then save.

Q: What's the difference between machine code and machine name?
A: Machine code is a unique identifier (e.g., "LATHE-001") used for quick lookup. Machine name is the full descriptive name (e.g., "Industrial Lathe Model X").

Q: Do I need to assign a machine to a work center?
A: Yes, every machine must be assigned to a work center (the department/area where it operates).

STATUS & EVENTS
Q: What are the different machine statuses?
A: ACTIVE (operating normally), UNDER_MAINTENANCE (scheduled maintenance), BREAKDOWN (unexpected failure), OUT_OF_SERVICE (no longer in use).

Q: How do I change a machine's status?
A: Go to the machine's details, click "Change Status," select the new status, enter a reason, and save. The system records who made the change and when.

Q: What happens when I log a BREAKDOWN event?
A: The system automatically changes the machine status to BREAKDOWN and records the time when the event started.

Q: Can I manually close an open event?
A: Yes, you can log an event without an end time, then close it later by logging an event indicating the problem resolved.

Q: Is there a difference between manually and system-generated events?
A: Yes. Manual events are entered by people. System events are generated automatically (e.g., automatic status updates). Both are recorded for audit purposes.

PRODUCTION TRACKING
Q: What should I record in production logs?
A: For each shift/day: target quantity (planned), actual quantity produced, rejected quantity, time machine ran, and time it was down.

Q: How is efficiency percentage calculated?
A: (Actual Quantity Produced / Planned Quantity) × 100. For example, if planned was 500 and actual was 485, efficiency is 97%.

Q: Why is my efficiency percentage below 100%?
A: This indicates you're not meeting targets. Reasons could be machine breakdowns, maintenance, quality issues, or operational inefficiencies.

Q: Can I edit production data after I've entered it?
A: Yes, you can go back and update production logs if you find an error.

Q: Does the system track machine downtime?
A: Yes, you enter downtime minutes in production logs. The system can correlate this with logged breakdown/maintenance events.

HISTORY & AUDIT TRAIL
Q: Can I see what changes were made to a machine?
A: Yes, the "Status History" shows all status changes with who made it, when, and why.

Q: How far back does the history go?
A: The system retains all historical records indefinitely for compliance purposes.

Q: Can I see who changed a machine's status?
A: Yes, the system records the person's username and the timestamp of every status change.

Q: Is my data backed up?
A: Yes, the system performs daily backups. Contact your IT administrator for details.

TROUBLESHOOTING
Q: I can't see a machine I just created. Why?
A: The machine might have been created by another user and not visible to you due to permissions. Contact your system administrator.

Q: The system says the machine code already exists. What should I do?
A: Machine codes must be unique. Use a different code, or check if the machine is already in the system (maybe under a different name).

Q: An event is still showing as "open" but the machine should be done. How do I close it?
A: Log a new event for when the problem ended (e.g., log a RUNNING event), and the system will close the previous event.

Q: My production efficiency report doesn't match my manual calculations. Why?
A: Ensure you've entered all production data correctly, including planned and actual quantities. If still incorrect, contact IT support.

Q: Why can't I delete a machine?
A: Machines are not deleted; they're deactivated (soft deleted) to preserve historical records for compliance. This is intentional.

Q: I see "SYSTEM" as the user who made a change. What does this mean?
A: The change was made automatically by the system rather than by a person. For example, automatic status updates from events.

PERMISSIONS & ACCESS
Q: I can't create a machine. Why?
A: You may not have the PRODUCTION_ADMIN or ADMIN role. Contact your system administrator to request access.

Q: Why can I see some machines but not others?
A: The system may have filters applied. Check if you're filtering by work center or status.

Q: Can I restrict who sees certain machines?
A: Yes, work with your IT administrator to set up role-based access if needed.

API & INTEGRATION
Q: Can I access machine data from another system?
A: Yes, the module provides REST APIs for integration. Contact your IT department for API documentation.

Q: Can I export production data?
A: Yes, most reports have export to CSV capability. Look for the export button in reports.

Q: Can I automate production log entries?
A: Yes, other systems can push production data to the API. Requires IT configuration.

---

## Prompt Set 5: Change Management & Release Notes

### Prompt Template: Release Notes & Version History

```
TASK: Generate Release Notes for Machine Assets Module version 1.0

DOCUMENT STRUCTURE:

HEADER
Product: NextGen Manager ERP - Machine Assets Module
Version: 1.0 (Production Release)
Release Date: February 2026
Compatibility: Spring Boot 3.0+, Java 17+

EXECUTIVE SUMMARY
The Machine Assets Management module provides comprehensive tracking and management of production machinery. This initial release includes core functionality for machine registration, status management, event logging, and production tracking with complete audit capability.

NEW FEATURES IN THIS RELEASE
1. Machine Registration & Management
   - Register machines with codes, names, and operating costs
   - Assign machines to work centers
   - Track 4-state machine status: Active, Under Maintenance, Breakdown, Out of Service

2. Real-Time Status Tracking
   - Manual and automatic status changes
   - Complete status history with audit trail
   - Reason tracking for status changes
   - User identification for accountability

3. Event Logging
   - Log machine events: Running, Idle, Breakdown, Maintenance
   - Automatic status correlation with events
   - Open event auto-close when new event logged
   - Support for manual and system-generated events

4. Production Metrics
   - Daily production logging by machine
   - Shift-aware tracking
   - Efficiency percentage calculation (Actual/Planned)
   - Rejection rate tracking

5. Reporting & Analysis
   - Production efficiency reports by machine
   - Status history audit trail
   - Event-based analytics
   - Pagination for large datasets

ENHANCEMENTS SINCE BETA
- Improved error messages with specific error codes
- Added comprehensive input validation
- Enhanced performance with database indexing
- Added REST API for system integration

KNOWN ISSUES & LIMITATIONS
- Event timeout handling requires manual event closure in some scenarios
- Bulk operations not yet available (planned for v1.1)
- Export to CSV/PDF not included (planned for v1.1)
- No real-time dashboard widget (planned for v1.1)

FIXED BUGS
- Fixed NullPointerException in service mapper
- Resolved duplicate code validation
- Fixed timezone inconsistency in date/time fields
- Corrected status update transaction handling

DEPRECATIONS & BREAKING CHANGES
None in initial release

UPGRADE INSTRUCTIONS
N/A (Initial release)

MIGRATION GUIDE
For users upgrading from previous versions: [details if applicable]

SYSTEM REQUIREMENTS
- Java: 17 or later
- Spring Boot: 3.0 or later
- Database: MySQL 8.0+ or PostgreSQL 12+
- Memory: Minimum 2GB recommended for production
- Storage: Minimum 10GB for initial database

INSTALLATION & SETUP
1. Download release package
2. Extract to application directory
3. Configure database connection in application.properties
4. Run database migrations
5. Start application: ./mvnw spring-boot:run
6. Verify: Navigate to http://localhost:8080/api/machine-details

PERFORMANCE IMPROVEMENTS
- Added pagination to prevent OOM on large datasets
- Implemented database indexes on frequently queried columns
- Query response time: <100ms on 100K+ records

API CHANGES
New Endpoints:
- GET /api/machine-details (with pagination)
- POST /api/machine-events
- GET /api/machines/{id}/status-history

Modified Endpoints:
- [List any changes to existing endpoints]

DATABASE CHANGES
New Tables:
- MachineDetails
- MachineEvent
- MachineProductionLog
- MachineStatusHistory

SECURITY UPDATES
- Role-based access control implemented
- All endpoints require authentication
- Sensitive data protected in responses

DOCUMENTATION
- User Guide: [link]
- Administrator Guide: [link]
- API Documentation: [link]
- Technical Specification: [link]

SUPPORT & FEEDBACK
- Issue Tracking: [link]
- User Forum: [link]
- Support Email: support@nextgenmanager.com

ROADMAP - NEXT RELEASES
v1.1 (Q2 2026):
- Bulk operations for machines
- CSV/PDF export functionality
- Real-time dashboard widgets
- Event timeout automation

v1.2 (Q3 2026):
- Advanced analytics and reporting
- Machine maintenance scheduling
- Predictive breakdown alerts
- Mobile app support

THANK YOU
Thank you for using Machine Assets Management. We appreciate your feedback and suggestions for future improvements.

---

## How to Use These Prompts

### Step 1: Select the Right Prompt
- **For non-technical users**: Use Prompt Set 1
- **For IT administrators**: Use Prompt Set 2 (first prompt)
- **For developers**: Use Prompt Set 2 (second prompt)
- **For managers**: Use Prompt Set 3
- **For support team**: Use Prompt Set 4
- **For releases**: Use Prompt Set 5

### Step 2: Copy & Customize
1. Copy the prompt that matches your needs
2. Replace placeholders like [Name], [Link], [Date]
3. Add specific information about your implementation

### Step 3: Submit to AI
```
I need you to act as a technical documentation writer.

[Paste the selected prompt here]

Please generate the documentation now. Focus on clarity and 
completeness. Format as Markdown for publishing to a web portal.
```

### Step 4: Review & Publish
- Review generated content for accuracy
- Customize organization names and terminology
- Add internal links and references
- Publish to your documentation portal or wiki

---

## Tips for Best Results

1. **Provide Context**: More context = better documentation
2. **Specify Audience**: Make sure AI knows who will read this
3. **Set Tone**: Specify professional, casual, technical, etc.
4. **Request Format**: Ask for Markdown, HTML, or PDF
5. **Ask for Examples**: Request real-world scenarios and code samples
6. **Iterate**: If output isn't perfect, provide feedback and refine

---

## Quality Checklist for Generated Documentation

After AI generates documentation, verify:

- [ ] **Accuracy**: Information matches your system
- [ ] **Completeness**: All features covered
- [ ] **Clarity**: Non-technical users can understand
- [ ] **Structure**: Logical flow and organization
- [ ] **Examples**: Real, relevant examples provided
- [ ] **Format**: Proper Markdown or chosen format
- [ ] **Links**: All internal/external links work
- [ ] **Images/Diagrams**: Placeholders marked for addition
- [ ] **Tone**: Consistent with brand voice
- [ ] **Compliance**: Covers legal/regulatory requirements

---

## Version Control for Generated Documentation

Keep track of documentation versions:

```
Version | Date | AI Model | Key Changes
--------|------|----------|-------------
1.0     | 2/25/26 | GPT-4   | Initial generation
1.1     | 3/15/26 | GPT-4   | Added API examples
1.2     | 4/01/26 | Claude  | Expanded troubleshooting
```

---

