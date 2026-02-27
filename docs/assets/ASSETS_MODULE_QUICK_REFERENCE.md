# Assets Module - Quick Reference & Implementation Checklist

**Document Version:** 1.0  
**Created:** February 25, 2026

---

## Quick Reference Guide

### Endpoint Quick Map

```
┌─ MACHINE DETAILS ────────────────────────────────────────────────┐
│ GET    /api/machine-details              → Fetch all machines    │
│ GET    /api/machine-details/{id}         → Fetch by ID           │
│ POST   /api/machine-details              → Create new machine    │
│ PUT    /api/machine-details/{id}         → Update machine        │
│ PATCH  /api/machine-details/{id}/status  → Change status         │
│ DELETE /api/machine-details/{id}         → Deactivate machine    │
└──────────────────────────────────────────────────────────────────┘

┌─ MACHINE EVENTS ─────────────────────────────────────────────────┐
│ POST   /api/machine-events               → Log event             │
│ [MISSING] GET /api/machine-events/{id}   → Fetch events          │
│ [MISSING] GET /api/machines/{id}/events  → Events by machine     │
└──────────────────────────────────────────────────────────────────┘

┌─ PRODUCTION LOGS ────────────────────────────────────────────────┐
│ POST   /api/machine-production-logs      → Create/Update log     │
│ GET    /api/machines/{id}/production-logs → Fetch production     │
└──────────────────────────────────────────────────────────────────┘

┌─ STATUS HISTORY ─────────────────────────────────────────────────┐
│ GET    /api/machines/{id}/status-history → Audit trail          │
└──────────────────────────────────────────────────────────────────┘
```

### Status State Machine

```
                     ┌─────────────────────┐
                     │ UNDER_MAINTENANCE   │
                     └──────────┬──────────┘
                                │
                                ↑ (MAINTENANCE event)
                                │
                                ↓
                                │ (RUNNING event)
    ┌────────────────────────────┴────────────────────────────┐
    │                                                          │
    ▼                                                          ▼
┌────────┐  (BREAKDOWN event)  ┌──────────┐  (resolved)  ┌─────────────┐
│ ACTIVE │────────────────────→│ BREAKDOWN│─────────────→│ OUT_OF_SERVICE│
└────────┘                     └──────────┘              └─────────────┘
    ↑                                                          │
    │                                                          │
    └──────────────────────────────────────────────────────────┘
                         (ACTIVE → manual)
```

### Entity Relationships

```
MachineDetails (1) ──┬──→ (N) MachineEvent
                     ├──→ (N) MachineProductionLog
                     └──→ (N) MachineStatusHistory
                     │
                     └──→ (N) WorkCenter (via workCenterId)
```

### Enum Reference

**MachineStatus**
- ACTIVE
- UNDER_MAINTENANCE
- BREAKDOWN
- OUT_OF_SERVICE

**EventType**
- RUNNING
- IDLE
- BREAKDOWN
- MAINTENANCE

**Source**
- MANUAL (user-initiated)
- SYSTEM (automatic)

### Common HTTP Status Codes

| Code | Meaning | Example |
|------|---------|---------|
| 200 | Success | Machine retrieved |
| 201 | Created | Machine created |
| 400 | Bad Request | Invalid data |
| 401 | Unauthorized | No auth token |
| 403 | Forbidden | No permission |
| 404 | Not Found | Machine doesn't exist |
| 409 | Conflict | Duplicate code |
| 500 | Server Error | Database failure |

---

## Implementation Checklist

### Critical Issues to Fix (Priority 1)

- [ ] **Issue #1: Add @Autowired to MachineDetailsResponseMapper**
  - File: `MachineDetailsServiceImpl.java:35`
  - Status: ❌ Not Done
  - Fix: Add `@Autowired` annotation before field declaration
  - Estimated Time: 2 minutes
  - Testing: Unit test for service initialization
  - Risk Level: 🔴 HIGH (causes runtime failure)

- [ ] **Issue #2: Add GET methods to MachineEventController**
  - File: `MachineEventController.java`
  - Status: ❌ Not Done
  - Fix: Add `getEventsByMachineId()` and `getEventsByDateRange()` methods
  - Estimated Time: 30 minutes
  - Testing: Integration tests for event retrieval
  - Risk Level: 🟡 MEDIUM (missing functionality)

- [ ] **Issue #3: Implement pagination for getMachineList()**
  - File: `MachineDetailsServiceImpl.java:55`
  - Status: ❌ Not Done
  - Fix: Add PageRequest parameter, update controller
  - Estimated Time: 20 minutes
  - Testing: Unit test with large datasets
  - Risk Level: 🟡 MEDIUM (OOM risk on large data)

- [ ] **Issue #4: Add error response DTOs with error codes**
  - Files: All Controllers
  - Status: ❌ Not Done
  - Fix: Create ErrorResponseDTO, use specific error codes
  - Estimated Time: 45 minutes
  - Testing: Unit tests for error scenarios
  - Risk Level: 🟡 MEDIUM (poor API usability)

### High Priority Issues (Priority 2)

- [ ] **Issue #5: Add comprehensive validation**
  - File: `MachineEventRequestDTO.java`, `MachineProductionLogRequestDTO.java`
  - Status: ❌ Not Done
  - Fix: Add @NotNull, @Positive, @Min, @Max annotations
  - Estimated Time: 30 minutes
  - Testing: Validation tests with invalid inputs
  - Risk Level: 🟡 MEDIUM (invalid data acceptance)

- [ ] **Issue #6: Capture authenticated user in services**
  - Files: All Services that modify data
  - Status: ❌ Not Done
  - Fix: Inject SecurityContext, use getCurrentUser() method
  - Estimated Time: 45 minutes
  - Testing: Integration tests with authenticated requests
  - Risk Level: 🟡 MEDIUM (audit trail incomplete)

- [ ] **Issue #7: Add efficiency metrics to MachineProductionLog**
  - File: `MachineProductionLog.java`
  - Status: ❌ Not Done
  - Fix: Add @Transient calculated fields and database columns
  - Estimated Time: 20 minutes
  - Testing: Calculate against known values
  - Risk Level: 🟢 LOW (new feature)

- [ ] **Issue #8: Add search and filter endpoints**
  - File: `MachineDetailsController.java`
  - Status: ❌ Not Done
  - Fix: Add endpoints for work center, status, keyword search
  - Estimated Time: 60 minutes
  - Testing: Integration tests for each filter
  - Risk Level: 🟢 LOW (new feature)

- [ ] **Issue #9: Fix date/time inconsistency**
  - File: `MachineDetails.java`
  - Status: ❌ Not Done
  - Fix: Replace java.util.Date with java.time.LocalDateTime
  - Estimated Time: 30 minutes
  - Testing: Migration tests, timezone verification
  - Risk Level: 🔴 HIGH (data format change)

### Medium Priority Issues (Priority 3)

- [ ] **Issue #10: Implement event timeout mechanism**
  - File: `MachineEventServiceImpl.java`
  - Status: ❌ Not Done
  - Fix: Add scheduled task to close open events
  - Estimated Time: 90 minutes
  - Testing: Scheduled task tests
  - Risk Level: 🟡 MEDIUM (new feature)

- [ ] **Issue #11: Add batch operations**
  - File: `MachineDetailsController.java`
  - Status: ❌ Not Done
  - Fix: Create batch create/update endpoints
  - Estimated Time: 90 minutes
  - Testing: Bulk operation tests
  - Risk Level: 🟢 LOW (new feature)

- [ ] **Issue #12: Add export functionality**
  - Files: New controller methods
  - Status: ❌ Not Done
  - Fix: CSV export for production logs and machines
  - Estimated Time: 120 minutes
  - Testing: Export file validation
  - Risk Level: 🟢 LOW (new feature)

---

## Testing Checklist

### Unit Tests

#### MachineDetailsService
- [ ] Test create machine with valid data
- [ ] Test create machine with duplicate code (should fail)
- [ ] Test create machine with invalid work center (should fail)
- [ ] Test update machine status
- [ ] Test status change creates history record
- [ ] Test soft delete marks as deleted
- [ ] Test getMachineList returns only active
- [ ] Test negative cost validation

#### MachineEventService
- [ ] Test create event with valid data
- [ ] Test create event with null machine ID (should fail)
- [ ] Test create event with end time before start time (should fail)
- [ ] Test auto-close previous open event
- [ ] Test machine status updated on event creation
- [ ] Test duration calculation

#### MachineProductionLogService
- [ ] Test create production log with valid data
- [ ] Test create updates existing log (unique constraint)
- [ ] Test efficiency percentage calculation
- [ ] Test rejection rate calculation
- [ ] Test negative quantity validation
- [ ] Test pagination works correctly

#### MachineStatusHistoryService
- [ ] Test retrieve history for machine
- [ ] Test pagination of history
- [ ] Test sorting by date
- [ ] Test history contains all status changes

### Integration Tests

#### End-to-End Workflows
- [ ] Machine creation → Status change → Check history
- [ ] Machine event logging → Status update → Verify consistency
- [ ] Production log entry → Calculate efficiency → Verify metrics
- [ ] Multiple events → Ensure no overlaps → Verify auto-close

#### API Endpoint Tests
- [ ] GET endpoints return correct data
- [ ] POST endpoints create records
- [ ] PUT endpoints update records
- [ ] PATCH endpoints change status
- [ ] DELETE endpoints soft-delete
- [ ] Pagination parameters work
- [ ] Filtering parameters work
- [ ] Authentication enforced

#### Error Handling Tests
- [ ] Invalid machine ID returns 404
- [ ] Duplicate machine code returns 400
- [ ] Invalid enum value returns 400
- [ ] Missing required field returns 400
- [ ] Unauthorized access returns 403
- [ ] Database error returns 500

### Performance Tests

- [ ] getMachineList() with 10,000 machines completes in <500ms
- [ ] Event query with 1 year data completes in <1 second
- [ ] Production log aggregation runs efficiently
- [ ] Concurrent requests handled properly
- [ ] Connection pooling verified

---

## Configuration Reference

### Database Indexes

```sql
-- MachineDetails
CREATE INDEX idx_machine_code ON MachineDetails(machineCode);
CREATE INDEX idx_work_center ON MachineDetails(workCenterId);
CREATE INDEX idx_deleted_date ON MachineDetails(deletedDate);

-- MachineEvent
CREATE INDEX idx_me_machine_start_time ON machineEvent(machineId, startTime);
CREATE INDEX idx_me_end_time ON machineEvent(endTime);

-- MachineProductionLog
CREATE INDEX idx_mpl_machine_date ON machineProductionLog(machineId, productionDate);
CREATE INDEX idx_mpl_shift ON machineProductionLog(shiftId);

-- MachineStatusHistory
CREATE INDEX idx_msh_machine_changed_at ON machineStatusHistory(machineId, changedAt);
CREATE INDEX idx_msh_changed_by ON machineStatusHistory(changedBy);
```

### Required Permissions (Role-Based)

| Operation | SUPER_ADMIN | ADMIN | PRODUCTION_ADMIN | PRODUCTION_USER | USER |
|-----------|-------------|-------|------------------|-----------------|------|
| View Machines | ✓ | ✓ | ✓ | ✓ | ✓ |
| Create Machine | ✓ | ✓ | ✓ | ✗ | ✗ |
| Update Machine | ✓ | ✓ | ✓ | ✗ | ✗ |
| Delete Machine | ✓ | ✓ | ✗ | ✗ | ✗ |
| Log Events | ✓ | ✓ | ✓ | ✓ | ✓ |
| Change Status | ✓ | ✓ | ✓ | ✓ | ✗ |
| View History | ✓ | ✓ | ✓ | ✓ | ✓ |
| Export Data | ✓ | ✓ | ✓ | ✗ | ✗ |

---

## Troubleshooting Guide

### Common Issues & Solutions

#### 1. NullPointerException on MachineDetailsServiceImpl

**Symptom:** 
```
Exception: NullPointerException
at com.nextgenmanager.nextgenmanager.assets.service.MachineDetailsServiceImpl.getMachineDetailsById()
```

**Root Cause:** Missing @Autowired on MachineDetailsResponseMapper

**Solution:** 
```java
@Autowired  // ADD THIS LINE
private MachineDetailsResponseMapper machineDetailsResponseMapper;
```

#### 2. Machine Status Not Updating on Event

**Symptom:** Event is logged but machine status remains ACTIVE

**Root Cause:** Service method not called or transaction not committed

**Solution:** 
- Verify @Transactional annotation is present
- Check service method calls changeMachineStatus()
- Verify no exception is being silently caught

#### 3. OutOfMemoryError on getMachineList()

**Symptom:** 
```
Exception: java.lang.OutOfMemoryError: Java heap space
```

**Root Cause:** Loading all machines without pagination

**Solution:**
- Implement pagination with Pageable
- Set page size limit
- Use streams for large result sets

#### 4. Duplicate Machine Code Accepted

**Symptom:** Can create two machines with same code

**Root Cause:** Unique constraint not working (missing index)

**Solution:**
```sql
-- Add unique constraint
ALTER TABLE MachineDetails ADD CONSTRAINT uk_machine_code UNIQUE(machineCode);
```

#### 5. Status History Not Recorded

**Symptom:** Machine status changes but no history entry

**Root Cause:** saveStatusHistory() not called or transaction rolled back

**Solution:**
- Ensure saveStatusHistory() is called
- Verify MachineStatusHistoryRepository is injected
- Check transaction boundaries

---

## Performance Optimization Tips

### Query Optimization

1. **Always use pagination**
   ```java
   // BAD
   List<Machine> all = machineDetailsRepository.findAll();
   
   // GOOD
   Page<Machine> paged = machineDetailsRepository.findAll(
       PageRequest.of(0, 20)
   );
   ```

2. **Use projection for read-only queries**
   ```java
   // Fetch only required fields
   @Query("SELECT new MachineDTO(m.id, m.machineCode, m.machineName) " +
          "FROM MachineDetails m WHERE m.deletedDate IS NULL")
   List<MachineDTO> findActiveProjection();
   ```

3. **Use eager loading for relationships**
   ```java
   @ManyToOne(fetch = FetchType.EAGER)  // Load immediately
   private WorkCenter workCenter;
   ```

4. **Archive old data**
   ```sql
   -- Move production logs older than 1 year to archive table
   INSERT INTO MachineProductionLog_Archive
   SELECT * FROM MachineProductionLog
   WHERE productionDate < DATE_SUB(NOW(), INTERVAL 1 YEAR);
   ```

### Caching Strategy

```java
@Cacheable("machines")  // Cache result for 5 minutes
public MachineDetailsResponseDTO getMachineDetailsById(long id) {
    // Implementation
}

@CacheEvict(value = "machines", key = "#id")  // Invalidate cache
public void updateMachineDetails(long id, MachineDetails machine) {
    // Implementation
}
```

### Connection Pool Tuning

```properties
# application.properties
spring.datasource.hikari.maximum-pool-size=20
spring.datasource.hikari.minimum-idle=5
spring.datasource.hikari.connection-timeout=20000
```

---

## Version History

| Version | Date | Changes |
|---------|------|---------|
| 1.0 | 2026-02-25 | Initial comprehensive review with 12 identified issues |

---

## Contact & Support

- **Lead Developer:** [Name]
- **QA Lead:** [Name]
- **Documentation:** [Name]
- **Last Review:** February 25, 2026

---

## Appendix A: Sample cURL Commands

### Create Machine
```bash
curl -X POST http://localhost:8080/api/machine-details \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer YOUR_TOKEN" \
  -d '{
    "machineCode": "LATHE-001",
    "machineName": "Industrial Lathe",
    "workCenterId": 5,
    "costPerHour": 150.00
  }'
```

### Log Event
```bash
curl -X POST http://localhost:8080/api/machine-events \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer YOUR_TOKEN" \
  -d '{
    "machineId": 1,
    "eventType": "BREAKDOWN",
    "startTime": "2026-02-25T10:30:00",
    "source": "MANUAL"
  }'
```

### Get Status History
```bash
curl -X GET "http://localhost:8080/api/machines/1/status-history?page=0&size=20" \
  -H "Authorization: Bearer YOUR_TOKEN"
```

---

## Appendix B: Database Migration Script

```sql
-- Phase 1: Add missing constraints and indexes
ALTER TABLE MachineDetails 
ADD CONSTRAINT uk_machine_code UNIQUE(machineCode),
ADD INDEX idx_work_center (workCenterId),
ADD INDEX idx_deleted_date (deletedDate);

-- Phase 2: Add missing columns for metrics
ALTER TABLE machineProductionLog
ADD COLUMN efficiencyPercentage DECIMAL(5,2),
ADD COLUMN rejectionRate DECIMAL(5,2),
ADD COLUMN totalAvailableMinutes INT;

-- Phase 3: Add event status tracking
ALTER TABLE machineEvent
ADD COLUMN status ENUM('OPEN', 'CLOSED', 'TIMEOUT') DEFAULT 'OPEN';

-- Phase 4: Add event timeout handling
ALTER TABLE machineEvent
ADD COLUMN timeoutMinutes INT DEFAULT 1440;  -- 24 hours default

-- Verify migrations
SELECT COUNT(*) as total_machines FROM MachineDetails WHERE deletedDate IS NULL;
SELECT COUNT(*) as total_events FROM machineEvent WHERE status = 'OPEN';
SELECT COUNT(*) as total_logs FROM machineProductionLog;
```

