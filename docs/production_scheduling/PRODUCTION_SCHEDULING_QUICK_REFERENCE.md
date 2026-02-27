# Production Scheduling - Quick Reference & Work Hours Breakdown

**Date:** February 21, 2026  
**Project:** NextGen Manager - Work Order Production Scheduling  
**Scope:** COMPLETE Implementation (not MVP)

---

## 📊 At a Glance

| What | Effort | Days | Priority |
|------|--------|------|----------|
| **Phase 1: Database Schema** | 10 hrs | 1.25 | 🔴 START HERE |
| **Phase 2: Repos & Mappers** | 5 hrs | 0.75 | 🔴 THEN |
| **Phase 3: Scheduling Algorithm** | 30 hrs | 3.75 | 🔴 CRITICAL |
| **Phase 4: Capacity Management** | 18 hrs | 2.25 | 🟠 IMPORTANT |
| **Phase 5: REST APIs** | 12 hrs | 1.5 | 🟠 THEN |
| **Phase 6: Testing & Perf** | 15 hrs | 2 | 🟠 FINAL |
| **TOTAL** | **90 hrs** | **11 days** | ✅ COMPLETE |

---

## 🎯 Current State Analysis

### ✅ What You Already Have:
- WorkOrder (with operations, work centers, routing)
- WorkOrderOperation (with sequence, duration from routing)
- WorkCenter (with capacity hours/day)
- MachineDetails (linked to work centers)
- Routing & RoutingOperation (setup/run times)
- Database migration framework (V1-V47, next = V48)
- Spring Data JPA, Security, Swagger setup

### ❌ What's Missing for Complete ProductionSchedule:

```
❌ ProductionSchedule table/entity
   - Scheduled start/end dates per operation
   - Track actual execution dates
   - Status (PRELIMINARY, FINALIZED, EXECUTING)

❌ OperationDependency table/entity
   - Links operations (predecessor → successor)
   - Dependency types (FS, SS, FF, SF)
   - Lag time between operations

❌ WorkCenterCapacity (NEW)
   - Daily capacity tracking
   - Utilization percentage
   - Max load limits

❌ WorkCenterLoad (NEW)
   - Daily load per work center
   - Allocated vs available minutes
   - Status tracking

❌ Scheduling Algorithm (COMPLEX)
   - Forward pass scheduling
   - Capacity validation
   - Dependency resolution
   - Bottleneck detection

❌ REST APIs
   - Schedule work order endpoint
   - View schedules endpoint
   - Capacity dashboard endpoint

❌ Tests & Performance
   - Unit tests for algorithm
   - Integration tests
   - Performance benchmarks
```

---

## 🛠️ Work Breakdown by Phase

### **PHASE 1: Database Schema (10 hours)**

**Files to Create:**
1. `V48__AddProductionScheduling.sql` (Migration)

**What's Included:**
- ProductionSchedule table + 4 indexes
- OperationDependency table + 2 indexes
- WorkCenterCapacity table
- WorkCenterLoad table
- Sequences for all 4 tables

**Why It Takes 10 Hours:**
- Design normalized schema (2 hrs)
- Write migration script (2 hrs)
- Test forward migration locally (2 hrs)
- Test backward/rollback (2 hrs)
- Fix any issues (2 hrs)

---

### **PHASE 2: Repositories & Mappers (5 hours)**

**Files to Create:**
1. `ProductionSchedule.java` (Entity)
2. `OperationDependency.java` (Entity)
3. `WorkCenterCapacity.java` (Entity)
4. `WorkCenterLoad.java` (Entity)
5. `ProductionScheduleRepository.java`
6. `OperationDependencyRepository.java`
7. `WorkCenterCapacityRepository.java`
8. `WorkCenterLoadRepository.java`
9. `ProductionScheduleMapper.java`
10. `OperationDependencyMapper.java`
11. `WorkCenterCapacityMapper.java`
12. `WorkCenterLoadMapper.java`
13. DTOs (8 classes)

**Why It Takes 5 Hours:**
- Entities + annotations: 1.5 hrs
- Repositories: 1 hr (mostly Copy-paste patterns)
- Mappers: 1.5 hrs
- DTOs: 1 hr

---

### **PHASE 3: Scheduling Algorithm (30 hours) ⭐ MOST COMPLEX**

**Files to Create:**
1. `ProductionSchedulingService.java` (Main service - 200+ lines)
2. `CapacityValidationService.java` (Capacity checking)
3. `DependencyResolutionService.java` (Dependency handling)
4. `TimeCalculationService.java` (Date/time utilities)

**Core Algorithm:**
```
For each WorkOrder:
  1. Load all operations (in sequence order)
  2. Load all dependencies between operations
  3. Forward Pass:
     For each operation:
       a. Calculate earliest start date
       b. Check if fits in work center capacity
       c. If YES: lock schedule
       d. If NO: shift to next available slot
  4. Create ProductionSchedule records
  5. Update WorkCenterLoad (daily summaries)
  6. Return result with dates + utilization
```

**Why It Takes 30 Hours:**
- Service skeleton: 2 hrs
- Forward pass algorithm: 8 hrs (most complex part)
- Capacity checking: 6 hrs
- Dependency resolution: 5 hrs
- Time calculations & helpers: 3 hrs
- Error handling & logging: 2 hrs
- Edge case handling: 4 hrs

**This is the "BRAIN" of the system - most complex and critical**

---

### **PHASE 4: Capacity Management (18 hours)**

**Files to Create:**
1. `WorkCenterShift.java` (Entity - NEW)
2. `HolidayCalendar.java` (Entity - NEW)
3. `V49__AddShiftsAndHolidays.sql` (Migration)
4. `WorkCenterShiftService.java`
5. `HolidayService.java`
6. `LoadBalancingService.java`
7. Enhanced `CapacityValidationService`

**What's Added:**
- Multi-shift support (MORNING, AFTERNOON, NIGHT)
- Holiday calendar (no scheduling on holidays)
- Shift breaks (lunch, 30 min coffee)
- Load balancing heuristics
- Maintenance windows

**Why It Takes 18 Hours:**
- Shift entities + migration: 4 hrs
- Holiday calendar implementation: 3 hrs
- Shift-aware capacity checking: 5 hrs
- Load balancing algorithms: 4 hrs
- Testing shift logic: 2 hrs

**This is "Optional" for MVP but adds realism**

---

### **PHASE 5: REST APIs (12 hours)**

**Files to Create:**
1. `ProductionScheduleController.java` (6 endpoints)
2. `OperationDependencyController.java` (3 endpoints)
3. `WorkCenterCapacityController.java` (3 endpoints)
4. `SchedulingReportController.java` (Optional: 2 endpoints)

**Endpoints:**
```
POST   /api/production-schedules/schedule-work-order
GET    /api/production-schedules/work-order/{id}
GET    /api/production-schedules/work-center/{id}
GET    /api/production-schedules/capacity-utilization
PUT    /api/production-schedules/{id}/status
DELETE /api/production-schedules/{id}

POST   /api/operation-dependencies
GET    /api/operation-dependencies/operation/{id}
DELETE /api/operation-dependencies/{id}

GET    /api/work-center-capacity/{id}
PUT    /api/work-center-capacity/{id}
GET    /api/work-center-capacity/utilization

GET    /api/scheduling-reports/bottlenecks
GET    /api/scheduling-reports/critical-path/{woId}
```

**Why It Takes 12 Hours:**
- Controllers + routing: 3 hrs
- Request/response handling: 2 hrs
- Error handling: 2 hrs
- Swagger annotations: 2 hrs
- Integration with services: 2 hrs
- Postman testing: 1 hr

---

### **PHASE 6: Tests & Optimization (15 hours)**

**Files to Create:**
1. `ProductionSchedulingServiceTest.java` (10+ test cases)
2. `CapacityValidationServiceTest.java` (8+ test cases)
3. `DependencyResolutionServiceTest.java` (6+ test cases)
4. `TimeCalculationServiceTest.java` (8+ test cases)
5. `ProductionSchedulingIntegrationTest.java` (5+ scenarios)

**Test Coverage:**
- Unit tests: 37+ test methods
- Integration tests: 5+ end-to-end scenarios
- Performance tests: 3 benchmarks
- Target coverage: >80%

**Why It Takes 15 Hours:**
- Writing unit tests: 5 hrs
- Writing integration tests: 4 hrs
- Performance testing: 2 hrs
- Fixing bugs found during testing: 2 hrs
- Optimization & profiling: 2 hrs

---

## 🎯 Work Distribution by Category

| Category | Hours | % |
|----------|-------|---|
| **Database & Schema** | 10 | 11% |
| **Entities & Data Access** | 5 | 6% |
| **Business Logic (Algorithm)** | 30 | 33% |
| **Capacity Management** | 18 | 20% |
| **APIs & Controllers** | 12 | 13% |
| **Testing** | 15 | 17% |
| **TOTAL** | **90** | **100%** |

---

## 📈 Complexity by Phase

| Phase | Complexity | Dependencies | Risks |
|-------|-----------|--------------|-------|
| 1 | 🟢 LOW | None | Migration syntax |
| 2 | 🟢 LOW | Phase 1 | Simple mappings |
| 3 | 🔴 HIGH | Phases 1-2 | Algorithm correctness |
| 4 | 🟠 MEDIUM | Phases 1-3 | Shift complexity |
| 5 | 🟠 MEDIUM | Phases 1-3 | API design |
| 6 | 🟠 MEDIUM | Phases 1-5 | Test coverage |

---

## ⏱️ Timeline Scenarios

### Scenario A: Full-Time Development
```
Week 1:
  Mon-Tue: Phase 1 (Database)
  Wed-Thu: Phase 2 (Entities)
  Fri: Phase 3 start

Week 2:
  Mon-Wed: Phase 3 (Scheduling Algorithm)
  Thu: Phase 3 testing
  Fri: Phase 4 start

Week 3:
  Mon-Tue: Phase 4 (Capacity)
  Wed: Phase 5 (APIs)
  Thu: Phase 5 continued
  Fri: Phase 6 (Testing)

Week 4 (Days 1-2):
  Mon-Tue: Phase 6 (Optimization)
  
✅ TOTAL: 11 business days = 2.5 weeks
```

### Scenario B: Part-Time (5 hrs/day)
```
Week 1-2: Phase 1 + Phase 2
Week 3-4: Phase 3
Week 5: Phase 4
Week 6: Phase 5
Week 7: Phase 6

✅ TOTAL: 4-5 weeks
```

### Scenario C: Part-Time (3 hrs/day)
```
7-8 weeks total (same work, spread over time)
```

---

## 🚀 Recommended Execution Path

### Option 1: Complete (11 days)
- Do Phases 1-6 sequentially
- Maximum features
- Production-ready
- Best for long-term

### Option 2: MVP + Later (6 days)
- Phases 1-3 only
- Core scheduling works
- Skip shifts/holidays (Phase 4)
- Can enhance later

### Option 3: Incremental (4-5 weeks)
- Phase 1-2: Foundation (3 days)
- Phase 3: Core algorithm (4 days)
- Phase 5: APIs to get feedback (2 days)
- Phase 4: Enhancements (3 days)
- Phase 6: Polish (2 days)
- Deploy after Phase 3 to get user feedback

---

## 📋 Implementation Order

**Recommended (Lowest Risk):**
1. ✅ Database migration (V48)
2. ✅ Entities (straightforward)
3. ✅ Repositories (copy-paste patterns)
4. ✅ Scheduling Service (most complex, but isolated)
5. ✅ Controllers (once service is done)
6. ✅ Tests (validates everything)

**DO NOT DO:**
- ❌ Start with tests before implementation (TDD overkill here)
- ❌ Skip database design (will regret it)
- ❌ Build controllers before service works
- ❌ Test with production data initially

---

## 💡 Key Skills Needed

| Skill | Phase | Difficulty |
|-------|-------|-----------|
| SQL & Database Design | 1 | 🟢 Medium |
| JPA/Hibernate Entities | 2 | 🟢 Easy |
| Spring Data Repositories | 2 | 🟢 Easy |
| Algorithm Design | 3 | 🔴 Hard |
| Date/Time Handling | 3-4 | 🟠 Medium |
| Spring Controllers | 5 | 🟢 Easy |
| JUnit Testing | 6 | 🟠 Medium |

---

## 🎯 Success Metrics

### Phase 1 (Database)
- [ ] Migration runs without errors
- [ ] All tables created
- [ ] Foreign keys work
- [ ] Indexes present

### Phase 2 (Entities)
- [ ] All entities load/save
- [ ] Relationships work
- [ ] Cascade behavior correct
- [ ] Soft delete works

### Phase 3 (Algorithm)
- [ ] Single operation schedules
- [ ] Multiple operations respect sequence
- [ ] Dependencies enforced
- [ ] Capacity limits respected
- [ ] Performance: 1000 ops in <5 sec

### Phase 4 (Capacity)
- [ ] Shifts respected
- [ ] Holidays skipped
- [ ] Load balanced
- [ ] Utilization calculated

### Phase 5 (APIs)
- [ ] POST endpoint creates schedule
- [ ] GET endpoints return data
- [ ] PUT endpoint updates
- [ ] DELETE endpoint removes
- [ ] Error handling works

### Phase 6 (Tests)
- [ ] >80% code coverage
- [ ] All tests pass
- [ ] Performance acceptable
- [ ] No memory leaks

---

## 🔄 Dependencies Between Phases

```
Phase 1 (Database)
    ↓
Phase 2 (Entities & Repos)
    ↓
Phase 3 (Algorithm) ← CRITICAL PATH
    ↓↓
Phase 5 (APIs) ← Can start after Phase 3
    ↓
Phase 6 (Tests)

Phase 4 (Capacity) can be done in parallel or after Phase 3
```

---

## 📚 Deliverables

### At End of Phase 1:
- Database migration script
- 4 database tables
- Logical schema diagram

### At End of Phase 2:
- 4 entities with relationships
- 4 repositories with queries
- 4 mappers
- 8 DTOs

### At End of Phase 3:
- Complete scheduling algorithm
- Works for simple and complex cases
- Respects constraints
- Performant

### At End of Phase 4:
- Shift management
- Holiday calendar
- Load balancing
- Enhanced capacity checking

### At End of Phase 5:
- 14 REST endpoints
- Swagger documentation
- Postman collection

### At End of Phase 6:
- 40+ test cases
- >80% coverage
- Performance benchmark results
- Deployment checklist

---

## 💾 File Structure (What You'll Create)

```
src/main/java/com/nextgenmanager/nextgenmanager/production/
├── model/
│   ├── ProductionSchedule.java (NEW)
│   ├── OperationDependency.java (NEW)
│   ├── WorkCenterCapacity.java (NEW)
│   ├── WorkCenterLoad.java (NEW)
│   ├── WorkCenterShift.java (NEW)
│   └── HolidayCalendar.java (NEW)
├── repository/
│   ├── ProductionScheduleRepository.java (NEW)
│   ├── OperationDependencyRepository.java (NEW)
│   ├── WorkCenterCapacityRepository.java (NEW)
│   ├── WorkCenterLoadRepository.java (NEW)
│   ├── WorkCenterShiftRepository.java (NEW)
│   └── HolidayCalendarRepository.java (NEW)
├── service/
│   ├── ProductionSchedulingService.java (NEW)
│   ├── CapacityValidationService.java (NEW)
│   ├── DependencyResolutionService.java (NEW)
│   ├── TimeCalculationService.java (NEW)
│   ├── WorkCenterShiftService.java (NEW)
│   ├── HolidayService.java (NEW)
│   └── LoadBalancingService.java (NEW)
├── controller/
│   ├── ProductionScheduleController.java (NEW)
│   ├── OperationDependencyController.java (NEW)
│   ├── WorkCenterCapacityController.java (NEW)
│   └── SchedulingReportController.java (NEW)
├── mapper/
│   ├── ProductionScheduleMapper.java (NEW)
│   ├── OperationDependencyMapper.java (NEW)
│   ├── WorkCenterCapacityMapper.java (NEW)
│   ├── WorkCenterLoadMapper.java (NEW)
│   ├── WorkCenterShiftMapper.java (NEW)
│   └── HolidayCalendarMapper.java (NEW)
└── dto/
    ├── ProductionScheduleDTO.java (NEW)
    ├── OperationDependencyDTO.java (NEW)
    ├── WorkCenterCapacityDTO.java (NEW)
    ├── WorkCenterLoadDTO.java (NEW)
    ├── SchedulingRequestDTO.java (NEW)
    ├── SchedulingResponseDTO.java (NEW)
    ├── WorkCenterShiftDTO.java (NEW)
    └── HolidayCalendarDTO.java (NEW)

src/main/resources/db/migration/
├── V48__AddProductionScheduling.sql (NEW)
└── V49__AddShiftsAndHolidays.sql (NEW)

src/test/java/com/nextgenmanager/nextgenmanager/production/
├── service/
│   ├── ProductionSchedulingServiceTest.java (NEW)
│   ├── CapacityValidationServiceTest.java (NEW)
│   ├── DependencyResolutionServiceTest.java (NEW)
│   ├── TimeCalculationServiceTest.java (NEW)
│   └── ProductionSchedulingIntegrationTest.java (NEW)
```

**Total New Files: 35+**

---

## 🎓 What You'll Learn

1. **Database Design** - Normalized schema with constraints
2. **JPA/Hibernate** - Complex relationships, mappings
3. **Algorithm Design** - Graph traversal, scheduling constraints
4. **Spring Services** - Layered architecture, dependency injection
5. **REST APIs** - Controller design, error handling
6. **Testing** - Unit, integration, performance tests
7. **Performance Tuning** - Indexes, query optimization

---

## 📞 Ready to Start?

I can help you with:

1. **Generate Phase 1 migration** (V48) - Takes 5 mins
2. **Create all entities** (Phase 2) - Takes 10 mins
3. **Scaffold repositories** (Phase 2) - Takes 5 mins
4. **Implement scheduling algorithm** (Phase 3) - Takes 2-3 hours
5. **All of the above** - Takes ~3 hours

**What would you like me to start with?**

---

## 🔗 Related Documents

- `PRODUCTION_SCHEDULING_COMPLETE_ANALYSIS.md` - Detailed breakdown
- `SOLO_DEVELOPER_ROADMAP.md` - Project phases
- `WORK_ORDER_MISSING_FEATURES_ANALYSIS.md` - Feature requirements

---

**Last Updated:** February 21, 2026  
**Status:** Ready for implementation  
**Effort:** 90 hours = 11 days full-time equivalent

---

## AI/ML Rollout Strategy (Value-First)

Implement in 2 stages:

1. During normal implementation (now):
- Keep scheduler deterministic and explainable.
- Capture AI-ready data for every operation:
  - planned start/end, actual start/end
  - work center, shift, setup/run time
  - delay reason, downtime reason, reschedule reason
  - capacity snapshot at decision time
- Persist scheduling decision trail (`reasonCode`, `reasonText`) so users can trust output.

2. After core engine is stable:
- ML Model 1: operation duration prediction.
- ML Model 2: late-delivery risk scoring.
- AI Assistant: ranked reschedule options (cost/time impact).

User-visible value appears first in this order:
1. Better date accuracy.
2. Early risk alerts.
3. One-click replan suggestions.
