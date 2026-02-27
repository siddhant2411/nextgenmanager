# Production Scheduling - Complete Work Breakdown

**Analysis Date:** February 21, 2026  
**Status:** Analyzing what's needed for COMPLETE ProductionSchedule implementation  
**Current State:** Auth (Basic) ✅ COMPLETE  
**Next Phase:** Production Scheduling (COMPLETE, not MVP)

---

## 📊 Executive Summary

**What You Have:**
- ✅ WorkOrder (basic, with plannedStartDate/EndDate but NO scheduling algorithm)
- ✅ WorkOrderOperation (has sequence number, work center, but NO dependencies)
- ✅ WorkCenter (with availableHoursPerDay, status)
- ✅ MachineDetails (linked to WorkCenter, has availableHoursPerDay)
- ✅ Routing & RoutingOperation (setup/run times defined)
- ✅ Database migration framework (V1-V47, using Flyway)
- ✅ Spring Data JPA, Spring Security, Lombok, Swagger

**What You're Missing:**
- ❌ ProductionSchedule entity (tracks scheduled dates per operation)
- ❌ OperationDependency entity (links operations: predecessor/successor)
- ❌ WorkCenterCapacity tracking (daily load calculation)
- ❌ Scheduling algorithm (forward pass, respects dependencies & capacity)
- ❌ Capacity validation service
- ❌ API endpoints for scheduling
- ❌ Unit tests for scheduling logic
- ❌ Shift definitions (currently assumes 8hr/day)
- ❌ Holiday calendar
- ❌ Load balancing heuristics

---

## 🎯 COMPLETE ProductionSchedule Implementation Scope

### **Total Effort: ~70-85 hours (Solo Developer)**

| Phase | Component | Hours | Complexity |
|-------|-----------|-------|-----------|
| **Phase 1** | Database Schema + Entities | 8-10 | Low |
| **Phase 2** | Repositories & Mappers | 4-5 | Low |
| **Phase 3** | Core Scheduling Algorithm | 25-30 | HIGH |
| **Phase 4** | Capacity & Validation | 15-20 | MEDIUM |
| **Phase 5** | REST APIs & Integration | 10-12 | MEDIUM |
| **Phase 6** | Unit Tests & Performance | 12-15 | MEDIUM |
| **TOTAL** | **ALL WORK FOR COMPLETE** | **75-85** | - |

**Timeline (Solo, Full-Time):**
- Phase 1-2: 2-3 days
- Phase 3: 3-4 days (critical path)
- Phase 4: 2-3 days
- Phase 5: 1-2 days
- Phase 6: 2 days
- **Total: 10-14 days** (~2.5 weeks full-time)

---

## 📋 PHASE 1: Database Schema & Entities (8-10 hours)

### What needs to be created:

#### 1. **ProductionSchedule Entity**
```java
- id (Long, PK)
- workOrder (FK)
- operation (FK to WorkOrderOperation)
- workCenter (FK)
- scheduledStartDate (LocalDateTime)
- scheduledEndDate (LocalDateTime)
- actualStartDate (LocalDateTime, nullable)
- actualEndDate (LocalDateTime, nullable)
- status (ENUM: PRELIMINARY, FINALIZED, EXECUTING, COMPLETED)
- bufferTimeMinutes (int - safety buffer)
- createdDate, updatedDate, deletedDate
- Indexes: workOrderId, operationId, workCenterId, status
```

#### 2. **OperationDependency Entity**
```java
- id (Long, PK)
- predecessorOperation (FK to WorkOrderOperation)
- successorOperation (FK to WorkOrderOperation)
- dependencyType (ENUM: FS, SS, FF, SF)
  - FS = Finish-to-Start (standard)
  - SS = Start-to-Start (parallel start)
  - FF = Finish-to-Finish (parallel end)
  - SF = Start-to-Finish (rare)
- lagDays (int - offset between predecessor end and successor start)
- createdDate, updatedDate, deletedDate
- Unique constraint: (predecessor, successor)
- Indexes: predecessorId, successorId
```

#### 3. **WorkCenterCapacity Entity** (NEW)
```java
- id (Long, PK)
- workCenter (FK, UNIQUE)
- availableHoursPerDay (Decimal - from WorkCenter, duplicated for scheduling)
- maxUtilizationPercent (int - default 85%)
- createdDate, updatedDate, deletedDate
```

**NOTE:** WorkCenter already has `availableHoursPerDay`, but WorkCenterCapacity will:
- Decouple scheduling concerns from asset definitions
- Allow different capacity configs per scheduling scenario
- Track utilization tracking

#### 4. **WorkCenterLoad Entity** (Daily Tracking)
```java
- id (Long, PK)
- workCenter (FK)
- loadDate (LocalDate)
- allocatedMinutes (int - actual minutes scheduled)
- availableMinutes (int - total capacity in minutes)
- utilizationPercent (Decimal)
- status (ENUM: PLANNED, EXECUTING, COMPLETED)
- createdDate, updatedDate, deletedDate
- Unique constraint: (workCenterId, loadDate)
- Indexes: workCenterId, loadDate
```

#### 5. **OperationDependencyType Lookup** (Optional - can skip)
```sql
- Just use ENUM in OperationDependency
- Types: FS, SS, FF, SF
```

### Database Migration Script (V48):

```sql
-- ProductionSchedule table
CREATE SEQUENCE production_schedule_seq START 1 INCREMENT 50;
CREATE TABLE production_schedule (
  id BIGINT PRIMARY KEY,
  work_order_id INT NOT NULL,
  operation_id BIGINT NOT NULL,
  work_center_id INT NOT NULL,
  scheduled_start_date TIMESTAMP,
  scheduled_end_date TIMESTAMP,
  actual_start_date TIMESTAMP,
  actual_end_date TIMESTAMP,
  status VARCHAR(20) NOT NULL DEFAULT 'PRELIMINARY',
  buffer_time_minutes INT DEFAULT 0,
  created_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_date TIMESTAMP,
  deleted_date TIMESTAMP,
  FOREIGN KEY (work_order_id) REFERENCES work_order(id),
  FOREIGN KEY (operation_id) REFERENCES work_order_operation(id),
  FOREIGN KEY (work_center_id) REFERENCES work_center(id),
  UNIQUE (work_order_id, operation_id)
);

CREATE INDEX idx_ps_work_order ON production_schedule(work_order_id);
CREATE INDEX idx_ps_operation ON production_schedule(operation_id);
CREATE INDEX idx_ps_work_center ON production_schedule(work_center_id);
CREATE INDEX idx_ps_status ON production_schedule(status);

-- OperationDependency table
CREATE SEQUENCE operation_dependency_seq START 1 INCREMENT 50;
CREATE TABLE operation_dependency (
  id BIGINT PRIMARY KEY,
  predecessor_operation_id BIGINT NOT NULL,
  successor_operation_id BIGINT NOT NULL,
  dependency_type VARCHAR(10) DEFAULT 'FS',
  lag_days INT DEFAULT 0,
  created_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_date TIMESTAMP,
  deleted_date TIMESTAMP,
  FOREIGN KEY (predecessor_operation_id) REFERENCES work_order_operation(id),
  FOREIGN KEY (successor_operation_id) REFERENCES work_order_operation(id),
  UNIQUE (predecessor_operation_id, successor_operation_id)
);

CREATE INDEX idx_od_predecessor ON operation_dependency(predecessor_operation_id);
CREATE INDEX idx_od_successor ON operation_dependency(successor_operation_id);

-- WorkCenterCapacity table
CREATE SEQUENCE work_center_capacity_seq START 1 INCREMENT 50;
CREATE TABLE work_center_capacity (
  id BIGINT PRIMARY KEY,
  work_center_id INT NOT NULL UNIQUE,
  available_hours_per_day NUMERIC(10,2),
  max_utilization_percent INT DEFAULT 85,
  created_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_date TIMESTAMP,
  deleted_date TIMESTAMP,
  FOREIGN KEY (work_center_id) REFERENCES work_center(id)
);

-- WorkCenterLoad table (daily tracking)
CREATE SEQUENCE work_center_load_seq START 1 INCREMENT 50;
CREATE TABLE work_center_load (
  id BIGINT PRIMARY KEY,
  work_center_id INT NOT NULL,
  load_date DATE NOT NULL,
  allocated_minutes INT DEFAULT 0,
  available_minutes INT,
  utilization_percent NUMERIC(5,2),
  status VARCHAR(20) DEFAULT 'PLANNED',
  created_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_date TIMESTAMP,
  deleted_date TIMESTAMP,
  UNIQUE (work_center_id, load_date)
);

CREATE INDEX idx_wcl_work_center ON work_center_load(work_center_id);
CREATE INDEX idx_wcl_load_date ON work_center_load(load_date);
```

---

## 📋 PHASE 2: Repositories & Mappers (4-5 hours)

### Repositories to create:
1. **ProductionScheduleRepository** - Spring Data JPA
   - `findByWorkOrderId(int workOrderId)`
   - `findByOperationId(Long operationId)`
   - `findByWorkCenterId(int workCenterId)`
   - `findByStatusAndDateBetween(status, start, end)`

2. **OperationDependencyRepository** - Spring Data JPA
   - `findByPredecessorOperation(Long opId)`
   - `findBySuccessorOperation(Long opId)`
   - `findAllByPredecessorOperation_WorkOrder(WorkOrder wo)`

3. **WorkCenterCapacityRepository** - Spring Data JPA
   - `findByWorkCenter(WorkCenter wc)`
   - `findByWorkCenterId(int wcId)`

4. **WorkCenterLoadRepository** - Spring Data JPA
   - `findByWorkCenterAndLoadDate(WorkCenter wc, LocalDate date)`
   - `findByWorkCenterAndLoadDateBetween(int wcId, LocalDate start, LocalDate end)`

### Mappers to create:
1. **ProductionScheduleMapper** - map Entity ↔ DTO
2. **OperationDependencyMapper** - map Entity ↔ DTO
3. **WorkCenterCapacityMapper** - map Entity ↔ DTO
4. **WorkCenterLoadMapper** - map Entity ↔ DTO

### DTOs to create:
1. `ProductionScheduleDTO` (request/response)
2. `OperationDependencyDTO` (request/response)
3. `WorkCenterCapacityDTO` (request/response)
4. `WorkCenterLoadDTO` (response)
5. `SchedulingRequestDTO` - to trigger scheduling
6. `SchedulingResponseDTO` - result with all scheduled items

---

## 📋 PHASE 3: Core Scheduling Algorithm (25-30 hours) ⭐ CRITICAL

### What is Scheduling?
Transform **WorkOrder → WorkOrderOperations** into **ProductionSchedules** with:
- Feasible start/end dates
- Respecting operation sequence (1→2→3)
- Respecting operation dependencies (FS, SS)
- NOT exceeding work center capacity

### Algorithm Overview:

```
1. LOAD PHASE
   - Get all operations from work order
   - Load operation dependencies
   - Load work center capacities
   
2. FORWARD PASS (Calculate earliest possible dates)
   - For each operation (in sequence order):
     a. Calculate earliest start (max of):
        - WorkOrder.plannedStartDate
        - Previous operation end (sequence)
        - Predecessor operation end + lag (dependencies)
     b. Calculate duration = setupTime + runTime from RoutingOperation
     c. Calculate earliest end = start + duration
     d. Check capacity: does this fit in work center on this day?
        - If YES: lock the schedule
        - If NO: shift forward to next available slot (greedy)
   
3. CAPACITY CHECK PHASE
   - For each work center:
     - Sum all scheduled minutes per day
     - Check if <= available capacity
     - If over: apply load balancing heuristics
   
4. BACKWARD PASS (Optional - for deadline checking)
   - If WorkOrder has dueDate:
     - Work backwards to check if schedule meets due date
     - If not: flag as HIGH RISK
   
5. OUTPUT
   - Create ProductionSchedule records for each operation
   - Create WorkCenterLoad records (daily summaries)
   - Return SchedulingResponseDTO with:
     - All scheduled operations
     - Total duration
     - Critical path (longest sequence)
     - Capacity utilization per work center
     - Conflicts/warnings if any
```

### Key Service Classes:

#### 1. **ProductionSchedulingService**
```java
public class ProductionSchedulingService {
  
  // Main entry point
  public SchedulingResponseDTO scheduleWorkOrder(WorkOrder wo);
  
  // Forward pass
  private List<ProductionScheduleDto> forwardPass(
      WorkOrder wo, 
      List<WorkOrderOperation> operations,
      Map<Long, List<OperationDependency>> dependencies
  );
  
  // Find earliest available slot for operation
  private LocalDateTime findEarliestAvailableSlot(
      WorkCenter wc,
      LocalDateTime earliestStart,
      int durationMinutes
  );
  
  // Check if capacity available
  private boolean isCapacityAvailable(
      WorkCenter wc,
      LocalDateTime start,
      LocalDateTime end,
      int durationMinutes
  );
  
  // Get/Create daily load record
  private WorkCenterLoad getOrCreateDailyLoad(WorkCenter wc, LocalDate date);
  
  // Update daily load
  private void updateDailyLoad(WorkCenterLoad load, int additionalMinutes);
  
  // Backward pass (optional)
  private void backwardPass(List<ProductionScheduleDto> schedule, WorkOrder wo);
}
```

#### 2. **CapacityValidationService**
```java
public class CapacityValidationService {
  
  // Validate single operation fits
  public boolean canScheduleOperation(
      WorkCenter wc,
      LocalDateTime start,
      LocalDateTime end
  );
  
  // Get available slots for operation
  public List<TimeSlot> findAvailableSlots(
      WorkCenter wc,
      int durationMinutes,
      LocalDateTime searchFrom,
      LocalDateTime searchUntil
  );
  
  // Get utilization percentage for a date
  public BigDecimal getUtilizationPercentage(
      WorkCenter wc,
      LocalDate date
  );
  
  // Get bottleneck work centers
  public List<WorkCenter> getBottleneckCenters(
      List<WorkOrder> workOrders,
      BigDecimal thresholdPercent
  );
}
```

#### 3. **DependencyResolutionService**
```java
public class DependencyResolutionService {
  
  // Build dependency map
  public Map<Long, List<OperationDependency>> buildDependencyMap(
      List<OperationDependency> dependencies
  );
  
  // Get predecessors of operation
  public List<WorkOrderOperation> getPredecessors(Long operationId);
  
  // Get successors of operation
  public List<WorkOrderOperation> getSuccessors(Long operationId);
  
  // Check for circular dependencies
  public boolean hasCircularDependencies(WorkOrder wo);
  
  // Calculate critical path
  public List<WorkOrderOperation> calculateCriticalPath(WorkOrder wo);
}
```

#### 4. **TimeCalculationService**
```java
public class TimeCalculationService {
  
  // Get operation duration from routing
  public int getOperationDurationMinutes(WorkOrderOperation op);
  
  // Calculate next available date (skip weekends)
  public LocalDateTime getNextWorkingDay(LocalDateTime date);
  
  // Check if date is holiday
  public boolean isHoliday(LocalDate date);
  
  // Add working days to date
  public LocalDateTime addWorkingDays(LocalDateTime date, int days);
}
```

### Complexity Breakdown:

| Sub-task | Hours | Why |
|----------|-------|-----|
| Service skeleton + repositories | 3 | Straightforward |
| Forward pass algorithm | 8 | Core logic, edge cases |
| Capacity validation logic | 6 | Checking availability |
| Dependency resolution | 5 | Topological sort, circular check |
| Time calculations | 3 | Date arithmetic, holidays |
| Error handling & logging | 2 | Debugging scheduling issues |
| **Subtotal Phase 3** | **27** | - |

---

## 📋 PHASE 4: Capacity & Validation (15-20 hours)

### What to implement:

#### 1. **Shift & Calendar Management** (NEW)
```java
public class WorkCenterShift {
  - id
  - workCenter (FK)
  - shiftName (MORNING, AFTERNOON, NIGHT)
  - startTime (HH:mm)
  - endTime (HH:mm)
  - daysOfWeek (Mon-Sun)
  - availableHours per shift
}

public class HolidayCalendar {
  - id
  - year
  - holidays (List of LocalDates)
}
```

These require:
- 2 new entities
- 1 database migration
- Services to load/cache

#### 2. **Enhanced Capacity Checking**
```java
- Account for shift schedules
- Account for shift breaks (e.g., 30 min lunch)
- Account for holidays (no scheduling)
- Maintenance windows (marked days unavailable)
- Buffer time between operations (e.g., setup time)
```

#### 3. **Load Balancing Heuristics**
```
- Greedy allocation (current approach)
- Least loaded work center (distribute load)
- Earliest available slot
- Round-robin across equivalent work centers
```

### Services to enhance:
1. Extend `CapacityValidationService` with shift logic
2. Create `HolidayService` to manage holiday calendar
3. Create `WorkCenterShiftService` for shift management
4. Create `LoadBalancingService` for distribution strategies

### Hours breakdown:
- Shift/Calendar entities + migration: 3-4 hours
- Shift-aware capacity checking: 5-6 hours
- Holiday handling: 2-3 hours
- Load balancing: 3-4 hours
- **Subtotal: 13-17 hours**

---

## 📋 PHASE 5: REST APIs & Integration (10-12 hours)

### Controllers to create:

#### 1. **ProductionScheduleController**
```
POST   /api/production-schedules/schedule-work-order
       → triggers scheduling for a work order
       
GET    /api/production-schedules/work-order/{id}
       → get all scheduled operations for work order
       
GET    /api/production-schedules/work-center/{id}
       → get load for a work center (next 30 days)
       
GET    /api/production-schedules/capacity-utilization
       → global capacity dashboard
       
PUT    /api/production-schedules/{id}/status
       → update schedule status (PRELIMINARY → FINALIZED)
       
DELETE /api/production-schedules/{id}
       → remove a scheduled operation (reschedule)
```

#### 2. **OperationDependencyController**
```
POST   /api/operation-dependencies
       → add dependency between operations
       
GET    /api/operation-dependencies/operation/{id}
       → get all dependencies for operation
       
DELETE /api/operation-dependencies/{id}
       → remove dependency
```

#### 3. **WorkCenterCapacityController**
```
GET    /api/work-center-capacity/{id}
       → get capacity config for work center
       
PUT    /api/work-center-capacity/{id}
       → update capacity (e.g., reduce for maintenance)
       
GET    /api/work-center-capacity/utilization
       → all centers' utilization
```

#### 4. **SchedulingReportController** (Optional)
```
GET    /api/scheduling-reports/bottlenecks
       → which work centers are bottlenecks
       
GET    /api/scheduling-reports/critical-path/{woId}
       → critical path for work order
       
GET    /api/scheduling-reports/timeline/{woId}
       → Gantt chart data
```

### Integration Points:
1. Integrate with existing **WorkOrderService**
   - When WorkOrder status → RELEASED: auto-schedule
   - When operation added: validate & reschedule
   
2. Integrate with existing **WorkCenterService**
   - Extend to include capacity checks
   - Add capacity override endpoints
   
3. Error handling for:
   - No feasible schedule (over-capacity)
   - Circular dependencies detected
   - Missing routing data

### Hours breakdown:
- Schedule APIs: 3-4 hours
- Dependency APIs: 2 hours
- Capacity APIs: 2 hours
- Integration with existing services: 2-3 hours
- Error handling: 1-2 hours
- **Subtotal: 10-12 hours**

---

## 📋 PHASE 6: Unit Tests & Performance (12-15 hours)

### Test Classes to create:

#### 1. **ProductionSchedulingServiceTest**
```java
- Test forward pass with simple sequence
- Test with operation dependencies (FS, SS)
- Test capacity constraints (schedule respects limit)
- Test greedy allocation (shifts to next available)
- Test error cases (no feasible schedule)
- Test performance (1000 operations in <5 sec)
```

#### 2. **CapacityValidationServiceTest**
```java
- Test single operation fits
- Test multiple operations on same day
- Test shift boundaries
- Test holiday skipping
- Test utilization percentage calculation
```

#### 3. **DependencyResolutionServiceTest**
```java
- Test dependency map building
- Test circular dependency detection
- Test critical path calculation
- Test predecessor/successor retrieval
```

#### 4. **TimeCalculationServiceTest**
```java
- Test duration calculation from routing
- Test working day calculation
- Test holiday detection
- Test date arithmetic
```

#### 5. **Integration Tests**
```java
- End-to-end: Create WO → Schedule → Verify schedule
- Test with real routing times
- Test with multiple work centers
- Test with capacity constraints
```

### Performance Testing:
- 1000 operations, 10 work centers → <5 sec
- 10,000 operations → <30 sec
- Indexing optimization

### Test coverage target: >80%

### Hours breakdown:
- Unit tests (each service): 2-3 hours
- Integration tests: 2-3 hours
- Performance testing: 2 hours
- Optimization: 2-3 hours
- **Subtotal: 10-14 hours**

---

## 🎯 Complete Implementation Checklist

### PHASE 1: Database (V48 Migration)
- [ ] ProductionSchedule table + indexes
- [ ] OperationDependency table + indexes
- [ ] WorkCenterCapacity table
- [ ] WorkCenterLoad table
- [ ] Migration script V48
- [ ] Test migration (forward & backward)

### PHASE 2: Entities & Repositories
- [ ] ProductionSchedule.java entity
- [ ] OperationDependency.java entity
- [ ] WorkCenterCapacity.java entity
- [ ] WorkCenterLoad.java entity
- [ ] All 4 Repository interfaces
- [ ] All 4 Mapper classes
- [ ] All DTOs

### PHASE 3: Scheduling Algorithm
- [ ] ProductionSchedulingService (core)
- [ ] CapacityValidationService
- [ ] DependencyResolutionService
- [ ] TimeCalculationService
- [ ] Forward pass algorithm
- [ ] Capacity checking logic
- [ ] Error handling

### PHASE 4: Capacity Management
- [ ] WorkCenterShift entity (NEW)
- [ ] HolidayCalendar entity (NEW)
- [ ] Migration V49 (shifts + holidays)
- [ ] WorkCenterShiftService
- [ ] HolidayService
- [ ] LoadBalancingService
- [ ] Enhanced capacity validation

### PHASE 5: REST APIs
- [ ] ProductionScheduleController
- [ ] OperationDependencyController
- [ ] WorkCenterCapacityController
- [ ] SchedulingReportController (optional)
- [ ] All endpoints tested via Postman

### PHASE 6: Tests & Optimization
- [ ] ProductionSchedulingServiceTest
- [ ] CapacityValidationServiceTest
- [ ] DependencyResolutionServiceTest
- [ ] TimeCalculationServiceTest
- [ ] Integration tests
- [ ] Performance benchmarks
- [ ] Test coverage report

---

## 🎨 Architecture Diagram

```
WorkOrder
├── WorkOrderOperation (with sequence)
│   ├── RoutingOperation (setup/run times)
│   └── ProductionSchedule (scheduled dates) ← NEW
│       └── WorkCenterLoad (daily tracking) ← NEW
│
WorkOrderOperation
└── OperationDependency (FS, SS) ← NEW
    └── WorkOrderOperation (predecessor/successor)

WorkCenter
├── MachineDetails (machines assigned)
├── WorkCenterCapacity (capacity config) ← NEW
│   └── WorkCenterLoad (daily totals) ← NEW
├── WorkCenterShift (working hours) ← NEW
│   └── HolidayCalendar (days off) ← NEW
└── WorkCenterServiceImpl (add scheduling methods)
```

---

## 📊 Time Estimate Summary

| Phase | Task | Hours | Days (Solo) |
|-------|------|-------|------------|
| 1 | Database Schema | 10 | 1.25 |
| 2 | Repositories & Mappers | 5 | 0.75 |
| 3 | Scheduling Algorithm | 30 | 3.75 |
| 4 | Capacity Management | 18 | 2.25 |
| 5 | REST APIs | 12 | 1.5 |
| 6 | Tests & Optimization | 15 | 2 |
| **TOTAL** | **COMPLETE** | **90** | **11 days** |

**If working full-time:** ~2.5 weeks  
**If working part-time (5 hrs/day):** ~4-5 weeks  
**If working part-time (3 hrs/day):** ~7-8 weeks

---

## 🚀 Recommended Approach

### Option A: Complete Implementation (Recommended)
- Do all 6 phases
- Delivers fully-featured scheduling system
- 11 days full-time effort
- Future-proof and maintainable

### Option B: MVP + Later Enhancement (Faster)
- Do Phases 1-3 only (50 hours = 6-7 days)
- Skip shifts/holidays (Phase 4)
- Skip fancy reporting (Phase 5)
- Minimum testing (Phase 6)
- Then enhance later based on needs

### Option C: Phased Rollout
- **Week 1:** Phases 1-2 (Foundation)
- **Week 2:** Phase 3 (Core algorithm)
- **Week 3:** Phase 5 (APIs, get user feedback)
- **Week 4:** Phase 4 (Enhancements)
- **Week 5:** Phase 6 (Polish & optimize)

---

## 🛠️ Implementation Tips

### 1. **Start with Entities First**
   - Define all database tables
   - Run migrations locally
   - Verify schema

### 2. **Then Repositories**
   - Spring Data JPA makes this easy
   - Add custom queries as needed

### 3. **Build Algorithm Incrementally**
   - Forward pass without dependencies (step 1)
   - Add dependency checking (step 2)
   - Add capacity checking (step 3)
   - Test after each step

### 4. **Write Tests Early**
   - Test algorithm with hardcoded data first
   - Then integrate with real DB
   - Performance test with large datasets

### 5. **Reuse Existing Patterns**
   - Copy WorkOrderMapper pattern for new mappers
   - Copy WorkOrderRepository for new repositories
   - Follow existing naming conventions

### 6. **Use Postman/Insomnia**
   - Test each API as you build it
   - Export requests for documentation
   - Easier than manual testing

---

## 💡 Key Decisions You Need to Make

1. **Dependency Model:**
   - Support FS, SS, FF, SF or just FS & SS?
   - **Recommendation:** FS & SS (covers 95% of cases)

2. **Capacity Strategy:**
   - Greedy (current proposal) or optimal (complex)?
   - **Recommendation:** Greedy for v1, add heuristics later

3. **Shift Management:**
   - Single 8hr shift or multiple shifts?
   - **Recommendation:** Start with single shift, add complexity later

4. **Holiday Calendar:**
   - Hard-code holidays or allow admin to manage?
   - **Recommendation:** Admin-managed (flexibility)

5. **Load Balancing:**
   - Just check if it fits or distribute optimally?
   - **Recommendation:** Check if fits (simpler), v2 → optimize

---

## 🎯 Success Criteria

✅ **Phase 1 Complete:**
- Database migration runs without error
- All entities load/save correctly
- No constraint violations

✅ **Phase 2 Complete:**
- Repositories find data correctly
- Mappers convert Entity ↔ DTO
- Data integrity preserved

✅ **Phase 3 Complete:**
- Forward pass produces feasible schedule
- Operations scheduled respecting sequence
- Duration calculated correctly
- All unit tests pass

✅ **Phase 4 Complete:**
- Capacity never exceeded
- Shifts respected
- Holidays skipped
- Load balanced intelligently

✅ **Phase 5 Complete:**
- All APIs respond correctly
- Data can be viewed/modified
- Swagger docs accurate
- Postman tests pass

✅ **Phase 6 Complete:**
- >80% test coverage
- 1000 ops schedule in <5 sec
- No memory leaks
- Performance acceptable

---

## 📞 Next Steps

1. **Review this analysis** - Understand the full scope
2. **Decide on scope** - All 6 phases or MVP?
3. **Create Phase 1 migration** - I can generate this for you
4. **Create all entities** - I can scaffold these
5. **Start Phase 3** - Core algorithm (most complex part)

**What would you like me to do first?**
- Generate Phase 1 migration script (V48)?
- Create all entities & repositories?
- Start scheduling algorithm?
- All of the above?

Let me know! 🚀
