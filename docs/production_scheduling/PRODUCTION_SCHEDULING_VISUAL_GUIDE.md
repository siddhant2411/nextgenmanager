# Production Scheduling - Visual Implementation Guide

**Format:** Text diagrams, flowcharts, and visual representations  
**Use:** Print and keep beside your desk while coding

---

## 📊 System Architecture (Bird's Eye View)

```
┌─────────────────────────────────────────────────────────────┐
│                    NEXTGEN MANAGER                          │
└─────────────────────────────────────────────────────────────┘
                            ↓
        ┌───────────────────┴───────────────────┐
        ↓                                       ↓
  ┌──────────────┐                    ┌──────────────────┐
  │  Work Order  │                    │  Work Centers &  │
  │   System     │                    │    Machines      │
  │              │                    │                  │
  │ - WO (EXIST) │                    │ - WC (EXIST)     │
  │ - Operations │                    │ - Machines       │
  │ - Materials  │                    │ - Capacity       │
  └──────┬───────┘                    └──────────────────┘
         ↓
  ┌──────────────────────────────────────────────┐
  │    PRODUCTION SCHEDULING (YOUR NEW WORK)     │ ← YOU BUILD THIS
  │                                              │
  │  ┌────────────────────────────────────────┐  │
  │  │  ProductionSchedule Entity             │  │ When will each
  │  │  - schedules operations to dates       │  │ operation run?
  │  │  - tracks actual vs planned dates      │  │
  │  └────────────────────────────────────────┘  │
  │                                              │
  │  ┌────────────────────────────────────────┐  │
  │  │  OperationDependency Entity            │  │ How are operations
  │  │  - links operations together           │  │ connected?
  │  │  - FS (finish-to-start)                │  │
  │  │  - SS (start-to-start)                 │  │
  │  └────────────────────────────────────────┘  │
  │                                              │
  │  ┌────────────────────────────────────────┐  │
  │  │  WorkCenterCapacity + Load Entities    │  │ How much capacity?
  │  │  - tracks daily allocation             │  │ What's scheduled?
  │  │  - prevents overbooking                │  │
  │  └────────────────────────────────────────┘  │
  │                                              │
  │  ┌────────────────────────────────────────┐  │
  │  │  Scheduling Algorithm                 │  │ THE BRAIN
  │  │  - forward pass scheduling            │  │ (30 hours of work)
  │  │  - capacity validation                │  │
  │  │  - dependency resolution              │  │
  │  └────────────────────────────────────────┘  │
  │                                              │
  │  ┌────────────────────────────────────────┐  │
  │  │  REST APIs & Controllers               │  │ User interface
  │  │  - /schedule-work-order                │  │
  │  │  - /get-schedules                      │  │
  │  │  - /capacity-utilization               │  │
  │  └────────────────────────────────────────┘  │
  │                                              │
  └──────────────────────────────────────────────┘
         ↓
  ┌──────────────────────────────────────────────┐
  │           FRONTEND / USER INTERFACE          │
  │                                              │
  │  "Schedule this work order"                  │
  │          ↓                                   │
  │  Shows: When each operation runs             │
  │         Work center utilization              │
  │         Total project duration               │
  └──────────────────────────────────────────────┘
```

---

## 🔄 Scheduling Algorithm Flow (With Decisions)

```
START: User clicks "Schedule Work Order"
  │
  ├─→ Load WorkOrder with operations
  │   (Cut → Weld → Inspect)
  │
  ├─→ Load dependencies between operations
  │   (If any)
  │
  ├─→ Load work center capacities
  │   (WC-10: 480 min/day, WC-20: 480 min/day, etc.)
  │
  ├─→ FOR EACH OPERATION (in sequence):
  │   │
  │   ├─→ Calculate Earliest Start Date
  │   │   ├─ Max of:
  │   │   │  ├─ WorkOrder.plannedStartDate (e.g., 2026-03-01)
  │   │   │  ├─ Previous operation.endDate (e.g., 10:00)
  │   │   │  └─ Predecessor.endDate + lag (if dependency)
  │   │   └─ Result: 2026-03-01 10:00
  │   │
  │   ├─→ Get Operation Duration
  │   │   ├─ From RoutingOperation:
  │   │   │  ├─ setupTime (30 min)
  │   │   │  └─ runTime (90 min)
  │   │   └─ Total: 120 minutes
  │   │
  │   ├─→ Check: Can it fit in work center on that day?
  │   │   │
  │   │   ├─ WorkCenterLoad for 2026-03-01:
  │   │   │  ├─ Available: 480 minutes
  │   │   │  ├─ Already allocated: X minutes
  │   │   │  └─ Free space: 480 - X = Y minutes
  │   │   │
  │   │   ├─ Decision: Is 120 minutes <= Y minutes?
  │   │   │  │
  │   │   │  ├─ YES ✅
  │   │   │  │  ├─→ Schedule it on 2026-03-01
  │   │   │  │  ├─→ End time: 10:00 + 2 hours = 12:00
  │   │   │  │  └─→ Create ProductionSchedule record
  │   │   │  │
  │   │   │  └─ NO ❌
  │   │   │     ├─→ Move to next day (2026-03-02)
  │   │   │     ├─→ Check again (usually fits)
  │   │   │     └─→ Repeat until found slot
  │   │   │
  │   │   └─→ Update WorkCenterLoad
  │   │      (Add 120 minutes to 2026-03-01 allocation)
  │   │
  │   └─→ Next Operation
  │
  ├─→ ALL OPERATIONS SCHEDULED?
  │   │
  │   ├─ YES: Continue
  │   └─ NO: Return ERROR (no feasible schedule)
  │
  ├─→ Calculate Summary
  │   ├─ Total project duration: 4 hrs 45 min
  │   ├─ Start date: 2026-03-01 08:00
  │   ├─ End date: 2026-03-01 12:45
  │   └─ Work center utilization:
  │       ├─ WC-10: 25%
  │       ├─ WC-20: 28%
  │       └─ WC-30: 6%
  │
  └─→ RETURN: SchedulingResponseDTO
      ├─ scheduledOperations[]
      ├─ totalDuration
      ├─ workCenterUtilization{}
      └─ feasible: true/false
```

---

## 📊 Capacity Checking Logic (The Gatekeeper)

```
FUNCTION: canFitInCapacity(workCenter, date, durationMinutes)

    Get WorkCenterLoad for (workCenter, date)
         │
         ├─→ Does it exist?
         │   │
         │   ├─ YES: Load it
         │   │   ├─ allocated_minutes = 240 (already scheduled)
         │   │   ├─ available_minutes = 480 (total capacity)
         │   │   └─ free_space = 480 - 240 = 240 minutes
         │   │
         │   └─ NO: Create new
         │       ├─ allocated_minutes = 0
         │       ├─ available_minutes = 480
         │       └─ free_space = 480 minutes
         │
         └─→ Check: durationMinutes <= free_space?
             │
             ├─ YES (120 <= 240): ✅ CAN SCHEDULE
             │
             └─ NO (200 > 240): ❌ CANNOT SCHEDULE
                               → Try next day

```

---

## 🏗️ Database Relationships (ER Diagram in Text)

```
╔════════════════════════════════════════════════════════════════════╗
║                      PRODUCTION SCHEDULING                        ║
║                    ENTITY RELATIONSHIPS                           ║
╚════════════════════════════════════════════════════════════════════╝

workOrder (EXISTING)
  ├── id: int
  ├── workOrderNumber: "WO-001"
  ├── plannedStartDate: 2026-03-01
  ├── dueDate: 2026-03-15
  └─┬─ ONE-TO-MANY → workOrderOperation
    │
    └─ ONE-TO-MANY → ProductionSchedule (NEW)
       ├── id: long
       ├── scheduledStartDate: 2026-03-01 08:00
       ├── scheduledEndDate: 2026-03-01 10:00
       └── status: PRELIMINARY


workOrderOperation (EXISTING)
  ├── id: long
  ├── sequence: 1
  ├── operationName: "Cutting"
  ├── workCenterId: 10
  └─┬─ ONE-TO-MANY → OperationDependency (NEW)
    │                ├── id: long
    │                ├── dependencyType: "FS"
    │                └── lagDays: 0
    │
    ├─ MANY-TO-ONE → WorkCenter
    │
    └─ ONE-TO-MANY → ProductionSchedule (NEW)


OperationDependency (NEW)
  ├── id: long
  ├── predecessorOperationId: 1 (FK → WorkOrderOperation)
  ├── successorOperationId: 2 (FK → WorkOrderOperation)
  ├── dependencyType: "FS" (Finish-to-Start)
  └── lagDays: 1 (1 day gap required)


WorkCenter (EXISTING)
  ├── id: int
  ├── centerCode: "WC-10"
  ├── centerName: "Cutting Section"
  ├── availableHoursPerDay: 8
  └─┬─ ONE-TO-MANY → ProductionSchedule (NEW)
    │
    ├─ ONE-TO-MANY → WorkCenterCapacity (NEW)
    │                ├── id: long
    │                ├── availableHoursPerDay: 8
    │                └── maxUtilizationPercent: 85
    │
    └─ ONE-TO-MANY → WorkCenterLoad (NEW)
                    ├── id: long
                    ├── loadDate: 2026-03-01
                    ├── allocatedMinutes: 240
                    ├── availableMinutes: 480
                    └── utilizationPercent: 50%


RoutingOperation (EXISTING)
  ├── setupTime: 0.5 (hours)
  ├── runTime: 1.5 (hours)
  └── Total Duration = setupTime + runTime = 2 hours
```

---

## 🚀 Implementation Phases (Progress Tracker)

```
PHASE 1: Database & Migration (V48)
┌────────────────────────────────────────┐
│ □ Design schema                        │ 2 hrs
│ □ Write migration script               │ 2 hrs
│ □ Test forward migration               │ 2 hrs
│ □ Test rollback                        │ 2 hrs
│ □ Fix any issues                       │ 2 hrs
│                                        │
│ TOTAL: 10 hours                        │
│ STATUS: ________________               │
└────────────────────────────────────────┘

PHASE 2: Entities & Repositories
┌────────────────────────────────────────┐
│ □ ProductionSchedule.java             │ 1 hr
│ □ OperationDependency.java            │ 1 hr
│ □ WorkCenterCapacity.java             │ 0.5 hr
│ □ WorkCenterLoad.java                 │ 0.5 hr
│ □ All 4 Repositories                  │ 1 hr
│ □ All 4 Mappers                       │ 1 hr
│ □ All DTOs (8 files)                  │ 1 hr
│                                        │
│ TOTAL: 5 hours                         │
│ STATUS: ________________               │
└────────────────────────────────────────┘

PHASE 3: Scheduling Algorithm ⭐ CORE
┌────────────────────────────────────────┐
│ □ ProductionSchedulingService          │ 8 hrs
│ □ CapacityValidationService           │ 6 hrs
│ □ DependencyResolutionService         │ 5 hrs
│ □ TimeCalculationService              │ 3 hrs
│ □ Error handling                      │ 2 hrs
│ □ Edge case handling                  │ 4 hrs
│ □ Testing & debugging                 │ 2 hrs
│                                        │
│ TOTAL: 30 hours                        │
│ STATUS: ________________               │
└────────────────────────────────────────┘

PHASE 4: Capacity Management
┌────────────────────────────────────────┐
│ □ WorkCenterShift entity + migration   │ 4 hrs
│ □ HolidayCalendar entity + migration   │ 3 hrs
│ □ Shift-aware capacity checking       │ 5 hrs
│ □ Holiday handling                    │ 2 hrs
│ □ Load balancing                      │ 4 hrs
│                                        │
│ TOTAL: 18 hours                        │
│ STATUS: ________________               │
└────────────────────────────────────────┘

PHASE 5: REST APIs
┌────────────────────────────────────────┐
│ □ ProductionScheduleController         │ 4 hrs
│ □ OperationDependencyController       │ 2 hrs
│ □ WorkCenterCapacityController        │ 2 hrs
│ □ SchedulingReportController          │ 2 hrs
│ □ Error handling & validation         │ 1 hr
│ □ Swagger documentation               │ 1 hr
│                                        │
│ TOTAL: 12 hours                        │
│ STATUS: ________________               │
└────────────────────────────────────────┘

PHASE 6: Testing & Optimization
┌────────────────────────────────────────┐
│ □ Service unit tests (5 × 2 hrs)       │ 10 hrs
│ □ Integration tests                    │ 3 hrs
│ □ Performance testing                  │ 1 hr
│ □ Optimization                         │ 1 hr
│                                        │
│ TOTAL: 15 hours                        │
│ STATUS: ________________               │
└────────────────────────────────────────┘

═══════════════════════════════════════════════════════════════
                    GRAND TOTAL: 90 hours
═══════════════════════════════════════════════════════════════
```

---

## 💻 IDE Shortcuts & Workflow Tips

```
INTELLIJ SHORTCUTS:
  Alt+Insert      → Generate code (getters/setters, constructor)
  Ctrl+D          → Duplicate line
  Ctrl+Shift+Up   → Move line up
  Ctrl+Alt+L      → Format code
  Shift+F6        → Rename
  Ctrl+Shift+/    → Block comment
  Ctrl+/          → Line comment

USEFUL PATTERNS:
  Create entity → Generate repository → Generate mapper
  → Generate DTOs → Create service → Create controller

TEST RUNNER:
  Right-click class → Run Tests
  Ctrl+Shift+F10  → Run current test
  Ctrl+Shift+F9   → Debug current test

DATABASE:
  Run migration: mvn flyway:migrate
  Check schema: SELECT * FROM information_schema.tables
               WHERE table_schema = 'public'
```

---

## 📈 Complexity Ramp-Up (What You'll Face)

```
EASY (Phase 1-2)
  Level: 🟢 Beginner
  Tasks: SQL, Entity annotations
  Time: 3 days
  Risk: Low
  
  "Just write straightforward SQL and Java classes"

MEDIUM (Phase 4-5)
  Level: 🟠 Intermediate  
  Tasks: Capacity logic, REST APIs
  Time: 5 days
  Risk: Medium
  
  "Requires good understanding of Spring patterns"

HARD (Phase 3) ⚠️ CRITICAL
  Level: 🔴 Advanced
  Tasks: Scheduling algorithm, constraint satisfaction
  Time: 4 days
  Risk: HIGH
  
  "This is where 80% of bugs will be"
  "But this is where the value is"

HARD (Phase 6)
  Level: 🔴 Advanced
  Tasks: Testing, debugging, optimization
  Time: 2 days
  Risk: High
  
  "Finding edge cases and fixing bugs"
```

---

## 🎯 Testing Strategy

```
UNIT TESTS (Per Service)
├─ Test happy path (normal operation)
├─ Test edge cases (empty list, null values)
├─ Test error conditions (no capacity)
└─ Test boundaries (exactly fits, doesn't fit)

INTEGRATION TESTS
├─ End-to-end: Create WO → Schedule → Verify
├─ Multi-WO: Schedule 10 work orders
├─ Complex deps: Multiple dependencies
└─ Real data: Use actual routing times

PERFORMANCE TESTS
├─ 100 operations: Should complete in <1 sec
├─ 1000 operations: Should complete in <5 sec
├─ 10000 operations: Should complete in <30 sec
└─ Memory: No leaks (monitor heap)

EXAMPLE TEST:
┌─────────────────────────────────────────────────┐
│ @Test                                           │
│ void testScheduleSimpleWorkOrder() {            │
│   // Setup                                      │
│   WorkOrder wo = createTestWO(3 operations);   │
│   int initialSize = productionScheduleRepo      │
│                      .findAll().size();         │
│   // Execute                                    │
│   SchedulingResponseDTO response =             │
│     service.scheduleWorkOrder(wo);             │
│   // Assert                                     │
│   assertEquals(3, response.getScheduled        │
│              Operations().size());              │
│   assertTrue(response.isFeasible());           │
│   assertTrue(response.getTotalDuration()       │
│              > 0);                              │
│ }                                               │
└─────────────────────────────────────────────────┘
```

---

## 🔗 File Dependency Chain

```
Step 1: Database Migration (V48)
   │
   ├─→ Step 2: Entities
   │           └─→ Repositories
   │               └─→ Mappers
   │                   └─→ DTOs
   │
   ├─→ Step 3: Services (depends on everything above)
   │           ├─ ProductionSchedulingService (main)
   │           ├─ CapacityValidationService
   │           ├─ DependencyResolutionService
   │           └─ TimeCalculationService
   │
   ├─→ Step 4: Controllers (depends on services)
   │           └─→ REST endpoints
   │
   └─→ Step 5: Tests (depends on everything)
               └─→ Validates all components

PARALLEL WORK POSSIBLE:
  • Repositories can be built while services are designed
  • DTOs can be created while entities are built
  • But services must wait for entities/repos
  • Controllers must wait for services
```

---

## 🐛 Common Bugs & How to Avoid Them

```
BUG #1: Operation scheduled before predecessor finishes
└─ FIX: Check predecessor.endDate + lag

BUG #2: Work center over-booked (20 operations on 1 day capacity)
└─ FIX: Validate capacity before saving

BUG #3: Operations not scheduled in sequence order
└─ FIX: Process operations in sequence order

BUG #4: Infinite loop when no capacity for days
└─ FIX: Add maximum lookahead limit (e.g., 365 days)

BUG #5: Circular dependency not detected
└─ FIX: Use graph traversal to detect cycles

BUG #6: Null pointer when work center has no machine
└─ FIX: Add null checks, or create default capacity

BUG #7: Time zone issues with dates
└─ FIX: Use LocalDateTime, not Date

BUG #8: Database transaction not rolled back on error
└─ FIX: Add @Transactional with rollbackOn

BUG #9: Performance slow with 1000 operations
└─ FIX: Add database indexes on foreign keys

BUG #10: Mapper returns null values
└─ FIX: Implement proper mapping logic, test with data
```

---

## 📊 Progress Tracking Sheet

```
WEEK 1:
  Mon: Phase 1 (Database) - Migration script
  Tue: Phase 1 - Testing migration
  Wed: Phase 2 (Entities) - All 4 entities
  Thu: Phase 2 (Repos) - All 4 repositories
  Fri: Phase 2 (DTOs) - All 8 DTOs

  Hours: 15 hrs / 90 total = 17% ✓

WEEK 2:
  Mon: Phase 3 (Services) - Skeleton & forward pass
  Tue: Phase 3 - Forward pass algorithm
  Wed: Phase 3 - Capacity validation
  Thu: Phase 3 - Error handling
  Fri: Phase 3 - Testing & debugging

  Hours: 30 hrs / 90 total = 33% ✓

WEEK 3:
  Mon: Phase 4 (Capacity) - Shifts & holidays
  Tue: Phase 4 - Shift-aware checking
  Wed: Phase 4 - Load balancing
  Thu: Phase 5 (APIs) - Controllers & endpoints
  Fri: Phase 5 - Swagger & testing

  Hours: 25 hrs / 90 total = 28% ✓

WEEK 4:
  Mon: Phase 6 (Tests) - Service tests
  Tue: Phase 6 - Integration tests
  Wed: Phase 6 - Performance tests
  Thu: Phase 6 - Optimization
  Fri: Phase 6 - Final testing & cleanup

  Hours: 20 hrs / 90 total = 22% ✓

═══════════════════════════════════════════════════════════
TOTAL: 90 hours across 4 weeks (11 business days)
═══════════════════════════════════════════════════════════
```

---

## 🚀 Launch Checklist

```
PRE-LAUNCH:
  [ ] All 90 hours of work completed
  [ ] Phase 1-6 fully implemented
  [ ] 40+ tests passing (>80% coverage)
  [ ] Performance acceptable (<5 sec for 1000 ops)
  [ ] No compilation errors
  [ ] No runtime errors
  [ ] Database migrations run cleanly
  [ ] APIs respond correctly

DOCUMENTATION:
  [ ] Swagger docs generated
  [ ] README updated with scheduling info
  [ ] Code comments for complex logic
  [ ] Database schema documented
  [ ] API examples provided

TESTING:
  [ ] Unit tests pass: mvn test
  [ ] Integration tests pass
  [ ] Manual API testing (Postman)
  [ ] Load testing (1000 ops)

DEPLOYMENT:
  [ ] Code merged to main branch
  [ ] Migration script included
  [ ] Tagged with version
  [ ] Deployed to staging
  [ ] Verified in staging environment

USERS:
  [ ] Can schedule work orders
  [ ] See capacity utilization
  [ ] Can update schedules
  [ ] Can view reports
  [ ] System performs well
```

---

## 🎯 Success Indicators

✅ **You'll know it's working when:**
- User clicks "Schedule" → Gets dates back in <2 seconds
- Operations scheduled in correct order
- No work center is over-booked
- Actual vs planned dates tracked
- 1000 operations schedule in <5 seconds
- All 40+ tests pass
- >80% code coverage

🔴 **Red flags if:**
- Scheduling takes >10 seconds
- Operations out of order
- Capacity exceeded
- Null pointer exceptions
- Tests failing
- Infinite loops
- Memory leaks

---

## 📞 You're Ready When

✅ You understand the algorithm (forward pass)
✅ You know the 4 database tables needed
✅ You understand work center capacity checking
✅ You can explain dependencies
✅ You know what Phase 3 will take most time

---

**Print this page and keep it at your desk while coding!** 📋

Last Updated: February 21, 2026
