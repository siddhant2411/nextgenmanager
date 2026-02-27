# Production Scheduling - Data Flow & Architecture

**Reference:** For understanding how scheduling works end-to-end

---

## 🔄 High-Level Data Flow

```
USER ACTION: "Schedule this Work Order"
        ↓
POST /api/production-schedules/schedule-work-order
        ↓
ProductionScheduleController.scheduleWorkOrder(workOrderId)
        ↓
ProductionSchedulingService.scheduleWorkOrder(WorkOrder)
        ↓
FORWARD PASS ALGORITHM
├─ Load all WorkOrderOperations (in sequence)
├─ Load all OperationDependencies
├─ For each operation:
│  ├─ Calculate earliest possible start date
│  ├─ Get operation duration from RoutingOperation
│  ├─ Check if fits in work center capacity
│  └─ If fits: lock schedule | If not: shift forward
├─ Create ProductionSchedule records
├─ Update WorkCenterLoad (daily summaries)
└─ Return SchedulingResponseDTO
        ↓
SchedulingResponseDTO returned to frontend
├─ All scheduled operations with dates
├─ Work center utilization %
├─ Total project duration
└─ Any warnings/conflicts
```

---

## 📊 Database Model Relationships

```
workOrder (EXISTING)
    ↓ 1-to-many
workOrderOperation (EXISTING)
    ↓ referenced by
    ├── ProductionSchedule (NEW) - tracks when it will run
    ├── OperationDependency (NEW) - links to other operations
    └── WorkCenter (EXISTING) - where it runs

WorkCenter (EXISTING)
    ↓ 1-to-many
    ├── MachineDetails (EXISTING) - what machines are here
    ├── WorkCenterCapacity (NEW) - how much capacity per day
    │   └── WorkCenterLoad (NEW) - daily load tracking
    ├── WorkCenterShift (NEW) - working hours
    │   └── HolidayCalendar (NEW) - days off
    └── ProductionSchedule (NEW) - operations scheduled here

RoutingOperation (EXISTING)
    ├── setupTime (e.g., 30 min)
    └── runTime (e.g., 2 hours)
    Total duration = setupTime + runTime
```

---

## 🎯 Example: Scheduling a Work Order

**Input:** WorkOrder WO-001
```json
{
  "workOrderId": 1,
  "workOrderNumber": "WO-001",
  "plannedQuantity": 100,
  "plannedStartDate": "2026-03-01",
  "dueDate": "2026-03-15",
  "operations": [
    {
      "id": 1,
      "sequence": 1,
      "operationName": "Cutting",
      "workCenterId": 10,
      "routingOperation": {
        "setupTime": 0.5,    // 30 minutes
        "runTime": 1.5       // 1.5 hours = 90 minutes per 100 qty
      }
    },
    {
      "id": 2,
      "sequence": 2,
      "operationName": "Welding",
      "workCenterId": 20,
      "routingOperation": {
        "setupTime": 0.25,   // 15 minutes
        "runTime": 2.0       // 2 hours = 120 minutes per 100 qty
      }
    },
    {
      "id": 3,
      "sequence": 3,
      "operationName": "Inspection",
      "workCenterId": 30,
      "routingOperation": {
        "setupTime": 0,
        "runTime": 0.5       // 30 minutes
      }
    }
  ]
}
```

---

## 🔄 Forward Pass Algorithm Step-by-Step

### Step 1: Initialize
```java
WorkOrder wo = workOrderRepository.findById(1);
List<WorkOrderOperation> operations = wo.getOperations();  // 3 operations
Map<Long, List<OperationDependency>> dependencies = 
    buildDependencyMap(wo);  // If any
Map<Integer, WorkCenterCapacity> capacities = 
    loadCapacities();  // All work centers

// For our example: no explicit dependencies
// Operations linked only by sequence (Op1 → Op2 → Op3)
```

### Step 2: Process Operation 1 (Cutting)
```
Operation: Cutting (ID=1)
WorkCenter: WC-10 (Cutting)
Duration: 30 min (setup) + 90 min (run) = 120 minutes

Earliest Start Date:
  - WorkOrder.plannedStartDate = 2026-03-01 08:00 (assume 8am)
  - No predecessor operations
  → Earliest Start = 2026-03-01 08:00

Check Capacity on 2026-03-01:
  - WC-10 available: 480 minutes (8 hours)
  - Already allocated: 0 minutes
  - Need: 120 minutes
  - Can fit? YES (120 <= 480)

Schedule:
  ProductionSchedule
    id: 1
    operation_id: 1
    work_center_id: 10
    scheduled_start_date: 2026-03-01 08:00
    scheduled_end_date: 2026-03-01 10:00 (08:00 + 2 hours)
    status: PRELIMINARY

Update WorkCenterLoad:
  WorkCenterLoad (2026-03-01 for WC-10)
    allocated_minutes: 120
    available_minutes: 480
    utilization_percent: 25%
```

### Step 3: Process Operation 2 (Welding)
```
Operation: Welding (ID=2)
WorkCenter: WC-20 (Welding)
Duration: 15 min (setup) + 120 min (run) = 135 minutes

Earliest Start Date:
  - WorkOrder.plannedStartDate = 2026-03-01 08:00
  - Previous operation (sequence) ends: 2026-03-01 10:00
  - No explicit dependencies
  → Earliest Start = 2026-03-01 10:00

Check Capacity on 2026-03-01:
  - WC-20 available: 480 minutes
  - Already allocated: 0 minutes
  - Need: 135 minutes
  - Can fit? YES (135 <= 480)

Schedule:
  ProductionSchedule
    id: 2
    operation_id: 2
    work_center_id: 20
    scheduled_start_date: 2026-03-01 10:00
    scheduled_end_date: 2026-03-01 12:15 (10:00 + 2.25 hours)
    status: PRELIMINARY

Update WorkCenterLoad:
  WorkCenterLoad (2026-03-01 for WC-20)
    allocated_minutes: 135
    available_minutes: 480
    utilization_percent: 28%
```

### Step 4: Process Operation 3 (Inspection)
```
Operation: Inspection (ID=3)
WorkCenter: WC-30 (QC)
Duration: 0 min (setup) + 30 min (run) = 30 minutes

Earliest Start Date:
  - WorkOrder.plannedStartDate = 2026-03-01 08:00
  - Previous operation ends: 2026-03-01 12:15
  → Earliest Start = 2026-03-01 12:15

Check Capacity on 2026-03-01:
  - WC-30 available: 480 minutes
  - Already allocated: 0 minutes
  - Need: 30 minutes
  - Can fit? YES (30 <= 480)

Schedule:
  ProductionSchedule
    id: 3
    operation_id: 3
    work_center_id: 30
    scheduled_start_date: 2026-03-01 12:15
    scheduled_end_date: 2026-03-01 12:45 (12:15 + 0.5 hours)
    status: PRELIMINARY

Update WorkCenterLoad:
  WorkCenterLoad (2026-03-01 for WC-30)
    allocated_minutes: 30
    available_minutes: 480
    utilization_percent: 6%
```

### Step 5: Return Response
```json
{
  "workOrderId": 1,
  "workOrderNumber": "WO-001",
  "status": "SCHEDULED",
  "scheduledOperations": [
    {
      "operationId": 1,
      "operationName": "Cutting",
      "workCenterId": 10,
      "scheduledStartDate": "2026-03-01T08:00:00",
      "scheduledEndDate": "2026-03-01T10:00:00"
    },
    {
      "operationId": 2,
      "operationName": "Welding",
      "workCenterId": 20,
      "scheduledStartDate": "2026-03-01T10:00:00",
      "scheduledEndDate": "2026-03-01T12:15:00"
    },
    {
      "operationId": 3,
      "operationName": "Inspection",
      "workCenterId": 30,
      "scheduledStartDate": "2026-03-01T12:15:00",
      "scheduledEndDate": "2026-03-01T12:45:00"
    }
  ],
  "totalDuration": "4 hours 45 minutes",
  "projectStartDate": "2026-03-01T08:00:00",
  "projectEndDate": "2026-03-01T12:45:00",
  "workCenterUtilization": {
    "WC-10": "25%",
    "WC-20": "28%",
    "WC-30": "6%"
  },
  "feasible": true,
  "warnings": []
}
```

---

## 🔴 Example: Over-Capacity Scenario

**Same work order, but WC-20 (Welding) is busy:**

```
Existing loads on 2026-03-01:
  WC-20: 350 minutes already scheduled

Processing Welding (Operation 2):
  Need: 135 minutes
  Available on 2026-03-01: 480 - 350 = 130 minutes
  Can fit? NO (135 > 130)
  
Action: SHIFT FORWARD TO NEXT AVAILABLE SLOT
  Check 2026-03-02 (next day):
    Available: 480 minutes (full)
    Need: 135 minutes
    Can fit? YES
  
New Schedule:
  Scheduled Start: 2026-03-02 08:00 (NEXT DAY)
  Scheduled End: 2026-03-02 10:15

Result:
  Welding operation DELAYED by 1 day
  Inspection also delays (depends on Welding)
  Project end date: 2026-03-02 10:45 (instead of 12:45)
```

---

## 📊 With Dependencies Example

**If we had an explicit dependency:**

```
OperationDependency
  predecessorOperation: Op1 (Cutting)
  successorOperation: Op3 (Inspection)
  dependencyType: "FS"  // Finish-to-Start
  lagDays: 1  // 1 day gap required (e.g., for drying)

Then:
  Op1 ends: 2026-03-01 10:00
  Op3 earliest start (without lag): 2026-03-01 10:00
  Op3 earliest start (with lag): 2026-03-02 10:00 (add 1 day)
  
This would override the sequence-based timing.
```

---

## 💾 Database State After Scheduling

### ProductionSchedule Table:
```sql
SELECT * FROM production_schedule WHERE work_order_id = 1;

| id | work_order_id | operation_id | work_center_id | scheduled_start_date | scheduled_end_date | status |
|-------|---|---|---|---|---|---|
| 1 | 1 | 1 | 10 | 2026-03-01 08:00 | 2026-03-01 10:00 | PRELIMINARY |
| 2 | 1 | 2 | 20 | 2026-03-01 10:00 | 2026-03-01 12:15 | PRELIMINARY |
| 3 | 1 | 3 | 30 | 2026-03-01 12:15 | 2026-03-01 12:45 | PRELIMINARY |
```

### WorkCenterLoad Table:
```sql
SELECT * FROM work_center_load WHERE load_date = '2026-03-01';

| id | work_center_id | load_date | allocated_minutes | available_minutes | utilization_percent |
|---|---|---|---|---|---|
| 1 | 10 | 2026-03-01 | 120 | 480 | 25.00 |
| 2 | 20 | 2026-03-01 | 135 | 480 | 28.13 |
| 3 | 30 | 2026-03-01 | 30 | 480 | 6.25 |
```

---

## 🔄 What Happens When You Update Operations

**User adds a new operation to the work order:**

```
PUT /api/work-orders/1/operations
{
  "operationName": "Packaging",
  "sequence": 4,
  "workCenterId": 40,
  "setupTime": 0.25,
  "runTime": 0.75
}

Application handles:
  1. Adds new WorkOrderOperation
  2. Detects workOrderStatus == "RELEASED" (already scheduled)
  3. AUTOMATIC RESCHEDULE:
     - Clears old ProductionSchedule records
     - Clears old WorkCenterLoad records
     - Runs scheduling algorithm again with new operation
  4. Returns new SchedulingResponseDTO with updated dates

Result: Schedule automatically updated, including new operation
```

---

## 🏗️ Service Layer Architecture

```
ProductionScheduleController
    ↓ calls
ProductionSchedulingService (ORCHESTRATOR)
    ├─ calls → DependencyResolutionService
    │           (build dependency map, check circular, critical path)
    ├─ calls → CapacityValidationService
    │           (check if operation fits, find available slots)
    ├─ calls → TimeCalculationService
    │           (duration, date arithmetic, holidays)
    ├─ calls → LoadBalancingService
    │           (decide which slot to use if multiple available)
    └─ calls → Repositories (save ProductionSchedule, WorkCenterLoad)

All Services use:
    ├── ProductionScheduleRepository
    ├── OperationDependencyRepository
    ├── WorkCenterCapacityRepository
    ├── WorkCenterLoadRepository
    ├── WorkOrderOperationRepository
    ├── WorkCenterRepository
    └── RoutingOperationRepository (for duration)
```

---

## 🎯 Key Algorithms

### Forward Pass (Sequential Scheduling)
```
For each operation in sequence order:
  1. start_date = max(
       workorder.planned_start_date,
       previous_operation.end_date,
       predecessor_operation.end_date + lag
     )
  
  2. duration = routing.setup_time + routing.run_time
  
  3. While NOT can_fit_in_capacity(work_center, start_date, duration):
       start_date = next_available_day(work_center, start_date)
  
  4. end_date = start_date + duration
  
  5. Save ProductionSchedule(start_date, end_date)
  6. Update WorkCenterLoad(start_date.date, +duration)
```

### Capacity Checking
```
can_fit_in_capacity(work_center, start_date, duration_minutes):
  load = WorkCenterLoad.find(work_center, start_date.date)
  return load.allocated_minutes + duration_minutes <= load.available_minutes
```

### Next Available Slot
```
next_available_day(work_center, from_date):
  current_date = from_date.date()
  while true:
    if is_holiday(current_date):
      current_date = add_days(current_date, 1)
      continue
    
    load = WorkCenterLoad.find(work_center, current_date)
    if load.allocated_minutes < load.available_minutes:
      return LocalDateTime.of(current_date, work_center.shift.start_time)
    
    current_date = add_days(current_date, 1)
```

---

## 📈 Scaling & Performance Considerations

### Current Algorithm Complexity:
- Time: O(n²) worst case (n = number of operations)
- Space: O(n) for dependency map
- For 1000 operations: ~1 million iterations (manageable in <5 sec)

### Optimization Opportunities:
1. **Caching:** Cache work center loads for date ranges
2. **Indexing:** Index on (work_center_id, load_date)
3. **Parallel Processing:** Schedule multiple work orders in parallel
4. **Batch Processing:** Schedule 100 WOs at night as batch job
5. **Heuristics:** Use simulated annealing for optimal allocation

---

## 🔐 Data Integrity Safeguards

### Constraints:
```sql
-- ProductionSchedule can't schedule before work order start
CHECK (scheduled_start_date >= work_order.planned_start_date)

-- Can't schedule past work order due date (warning only)
-- Handled in application logic

-- OperationDependency prevents circular links
-- Validated before save

-- WorkCenterLoad totals never exceed available capacity
-- Validated during scheduling
```

### Transaction Safety:
```java
@Transactional(rollbackOn = Exception.class)
public SchedulingResponseDTO scheduleWorkOrder(WorkOrder wo) {
  // Either all operations schedule OR none (all or nothing)
  // If any operation can't be scheduled: rollback entire scheduling
}
```

---

## 🎯 Example: API Usage

### Schedule a Work Order
```bash
curl -X POST http://localhost:8080/api/production-schedules/schedule-work-order \
  -H "Content-Type: application/json" \
  -d '{"workOrderId": 1}'
```

### Get Schedule for Work Order
```bash
curl -X GET http://localhost:8080/api/production-schedules/work-order/1
```

### Get Work Center Load
```bash
curl -X GET http://localhost:8080/api/production-schedules/work-center/10
```

### View Bottlenecks
```bash
curl -X GET http://localhost:8080/api/scheduling-reports/bottlenecks
```

---

## 📊 Metrics to Track

After implementing, measure:

1. **Schedule Accuracy:** % of operations completed on scheduled date
2. **Capacity Utilization:** Average % utilization per work center
3. **On-Time Delivery:** % work orders completed by due date
4. **Algorithm Performance:** Time to schedule 100/1000 operations
5. **Data Growth:** Rows in ProductionSchedule/WorkCenterLoad per month

---

## 🔗 Related Code Patterns

### Existing patterns to follow:

**Entity Pattern (from WorkOrder):**
```java
@Entity
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
@Table(name = "tableName")
@Where(clause = "deletedDate IS NULL")  // Soft delete
public class Entity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fkId")
    private Related related;
    
    @CreationTimestamp
    @Column(updatable = false)
    private Date creationDate;
    
    @UpdateTimestamp
    private Date updatedDate;
    
    private Date deletedDate;
}
```

**Repository Pattern (from WorkOrderRepository):**
```java
public interface EntityRepository extends JpaRepository<Entity, Long> {
    List<Entity> findByWorkOrderId(int workOrderId);
    Optional<Entity> findById(Long id);
}
```

**Service Pattern (existing services):**
```java
@Service
@Transactional
public class EntityService {
    @Autowired
    private EntityRepository repository;
    
    public Entity save(Entity entity) {
        return repository.save(entity);
    }
}
```

**Controller Pattern (existing controllers):**
```java
@RestController
@RequestMapping("/api/entities")
@CrossOrigin("*")
public class EntityController {
    @Autowired
    private EntityService service;
    
    @PostMapping
    public ResponseEntity<EntityDTO> create(@RequestBody EntityDTO dto) {
        Entity entity = service.save(mapper.toEntity(dto));
        return ResponseEntity.ok(mapper.toDTO(entity));
    }
}
```

---

## 🚀 Next Steps

1. **Review this data flow** - Understand the algorithm
2. **Create Phase 1 migration** - Database tables
3. **Create entities** - ProductionSchedule, OperationDependency, etc.
4. **Implement scheduling service** - The core algorithm
5. **Build controllers** - REST APIs
6. **Write tests** - Validate algorithm

Ready to start? Let me know!

---

**Last Updated:** February 21, 2026  
**Status:** Reference architecture for implementation
