# Work Order & BOM - Missing Must-Have Features for Manufacturing ERP

**Analysis Date:** February 14, 2026  
**Status:** Current implementation has core infrastructure, but lacks 12 critical enterprise features  
**Priority:** Critical for production-grade ERP system

---

## Executive Summary

The current Work Order and BOM implementation provides:
- ✓ Basic lifecycle management (CREATED → RELEASED → IN_PROGRESS → COMPLETED → CLOSED)
- ✓ Material tracking with scrap percentage handling
- ✓ Operation sequencing with work centers
- ✓ Scrap tracking (materials & operations) 
- ✓ BOM versioning with active/inactive states (only 1 active per item)
- ✓ Partial material issuance support
- ✓ Partial operation completion support

**Missing:** 12 critical features preventing full manufacturing ERP functionality

---

## Critical Missing Features (Priority Ranked)

### 1. 🔴 **Production Scheduling & Sequencing** - CRITICAL
**Impact Level:** BLOCKING - Production cannot be efficiently sequenced without this

#### Business Problem
- No automated scheduling algorithm (forward/backward pass, critical path)
- Operations have sequence numbers but no dependency/constraint modeling
- No infinite vs. finite capacity scheduling modes
- Bottleneck work centers not identified

#### Current Code Gaps
```
WorkOrder: has plannedStartDate/EndDate but no scheduling algorithm
WorkOrderOperation: 
  - Has sequence number
  - No predecessor/successor dependencies
  - No constraint linking between operations
```

#### Implementation Requirements
1. **Create `ProductionSchedule` entity**
   - Track scheduled operations by work center and time period
   - Store scheduled start/end dates for each operation
   - Track schedule status (PRELIMINARY, FINALIZED, EXECUTING)

2. **Create `OperationDependency` model**
   - Link predecessor to successor operations
   - Support dependency types: Finish-to-Start (FS), Start-to-Start (SS), Finish-to-Finish (FF)
   - Define lead/lag time between operations

3. **Implement Scheduling Algorithm**
   - Forward pass: earliest operation start dates
   - Backward pass: latest operation start dates
   - Calculate critical path and slack time
   - Check work center capacity availability

4. **Add Constraint-Based Scheduling**
   - Respect work center availability (shifts, maintenance)
   - Handle material availability constraints
   - Support order-based vs. job-based sequencing

#### Database Schema Additions
```sql
CREATE TABLE productionSchedule (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  workOrderId INT NOT NULL,
  operationId BIGINT NOT NULL,
  scheduledStartDate DATETIME,
  scheduledEndDate DATETIME,
  status ENUM('PRELIMINARY','FINALIZED','EXECUTING'),
  lastScheduledDate DATETIME,
  FOREIGN KEY (workOrderId) REFERENCES workOrder(id),
  FOREIGN KEY (operationId) REFERENCES WorkOrderOperation(id)
);

CREATE TABLE operationDependency (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  predecessorOperationId BIGINT NOT NULL,
  successorOperationId BIGINT NOT NULL,
  dependencyType ENUM('FS','SS','FF','SF'),
  lagTime INT,  -- in minutes
  FOREIGN KEY (predecessorOperationId) REFERENCES WorkOrderOperation(id),
  FOREIGN KEY (successorOperationId) REFERENCES WorkOrderOperation(id)
);
```

#### Dependencies
- Requires WorkCenter capacity model (Feature #2)
- Requires material availability checks (Feature #3)

---

### 2. 🔴 **Work Center Capacity Planning & Load Management** - CRITICAL
**Impact Level:** BLOCKING - Cannot execute feasible schedules without capacity awareness

#### Business Problem
- WorkCenter has `costPerHour` and `availableHoursPerDay` but:
  - Shift definitions are incomplete (list of shifts but no scheduling details)
  - No calendar for holidays/maintenance windows
  - No actual capacity utilization tracking
  - Cannot calculate available minutes per period
  - No load balancing capability

#### Current Code Gaps
```
WorkCenter:
  - costPerHour: defined
  - availableHoursPerDay: defined  
  - availableShifts: list only
  - Missing: calendar, load tracking, actual utilization
```

#### Implementation Requirements
1. **Create `WorkCenterCalendar` entity**
   - Define shifts (start time, end time, capacity %)
   - Mark holidays/maintenance blocks (unavailable)
   - Track shift-specific costs if different

2. **Create `WorkCenterLoad` entity**
   - Track cumulative operation time per work center per day/shift
   - Calculate utilization % (actual / available)
   - Identify bottleneck periods (>100% load)

3. **Implement Capacity Checking**
   - Before scheduling: verify available capacity exists
   - Calculate setup time + run time per operation
   - Flag over-capacity conditions
   - Suggest load balancing across shifts/days

4. **Add Utilization Metrics**
   - Actual vs. planned utilization by work center
   - Downtime tracking
   - OEE (Overall Equipment Effectiveness) calculation foundation

#### Database Schema Additions
```sql
CREATE TABLE workCenterShift (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  workCenterId INT NOT NULL,
  shiftName VARCHAR(50),
  startTime TIME,
  endTime TIME,
  capacityPercentage INT,  -- 100% or less for part-time shifts
  costPerHour DECIMAL(10,2),
  FOREIGN KEY (workCenterId) REFERENCES workCenter(id)
);

CREATE TABLE workCenterCalendar (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  workCenterId INT NOT NULL,
  calendarDate DATE,
  isAvailable BOOLEAN,
  reason VARCHAR(100),  -- 'Maintenance', 'Holiday', etc.
  FOREIGN KEY (workCenterId) REFERENCES workCenter(id)
);

CREATE TABLE workCenterLoad (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  workCenterId INT NOT NULL,
  loadDate DATE,
  shiftId BIGINT,
  allocatedMinutes INT,
  availableMinutes INT,
  utilizationPercent DECIMAL(5,2),
  FOREIGN KEY (workCenterId) REFERENCES workCenter(id),
  FOREIGN KEY (shiftId) REFERENCES workCenterShift(id)
);
```

#### Dependencies
- Requires ProductionSchedule (Feature #1)
- Enables MaterialRequirements validation

---

### 3. 🔴 **Multi-Level BOM Explosion & Material Requirements Planning (MRP)** - CRITICAL
**Impact Level:** BLOCKING - Cannot handle complex assemblies; manual planning errors

#### Business Problem
- BomPosition.childBom supports multi-level but:
  - No recursive explosion algorithm implemented
  - WorkOrder only explodes top-level BOM positions
  - Scrap factors not cascaded through assembly levels
  - No alternate/substitute component logic
  - No material requirement aggregation

#### Current Code Gaps
```
Bom: parent InventoryItem, has BomPosition list
BomPosition: references childBom but no recursive traversal
WorkOrderServiceImpl.addWorkOrder():
  - Only iterates direct BomPosition children
  - Doesn't recurse into sub-assemblies
  - Scrap % applied at each level but not cascaded mathematically
```

#### Implementation Requirements
1. **Create BOM Explosion Algorithm**
   - Recursive function to expand all assembly levels
   - Multiply quantities through each level
   - Cascade scrap factors (cumulative %)
   - Return flattened material list with quantities at item level

2. **Create `MaterialRequirement` entity**
   - Store exploded material requirements
   - Link to work order (one-to-many)
   - Track source (which BOM position leads to this material)
   - Update when BOM changes mid-execution

3. **Implement Alternate Component Logic**
   ```sql
   ALTER TABLE bomPosition ADD COLUMN isAlternate BOOLEAN;
   ALTER TABLE bomPosition ADD COLUMN alternateForPositionId INT;
   -- Allows selecting between components at production time
   ```

4. **Material Planning Integration**
   - Check material availability before work order release
   - Flag shortage items during scheduling
   - Create purchase requisitions for unavailable items
   - Link work order to inventory reservations

#### Database Schema Additions
```sql
CREATE TABLE materialRequirement (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  workOrderId INT NOT NULL,
  inventoryItemId INT NOT NULL,
  explosionLevel INT,  -- level in BOM hierarchy
  netRequiredQuantity DECIMAL(15,5),
  plannedRequiredQuantity DECIMAL(15,5),  -- with scrap %
  sourcePositionId INT,  -- which BOM position generated this
  status ENUM('REQUIRED','RESERVED','ISSUED','COMPLETE'),
  FOREIGN KEY (workOrderId) REFERENCES workOrder(id),
  FOREIGN KEY (inventoryItemId) REFERENCES inventoryItem(id),
  FOREIGN KEY (sourcePositionId) REFERENCES bomPosition(id)
);

-- Track cumulative scrap factor through assembly levels
CREATE TABLE bomPositionPath (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  workOrderId INT,
  bomPositionId INT,
  depth INT,
  cumulativeScrapPercent DECIMAL(8,4),
  FOREIGN KEY (workOrderId) REFERENCES workOrder(id),
  FOREIGN KEY (bomPositionId) REFERENCES bomPosition(id)
);
```

#### Service Implementation Pseudocode
```java
public List<MaterialRequirement> explodeBOM(WorkOrder workOrder) {
    return explodeBOMRecursive(
        workOrder.getBom(), 
        workOrder.getPlannedQuantity(), 
        BigDecimal.ZERO,  // cumulative scrap
        0  // level
    );
}

private List<MaterialRequirement> explodeBOMRecursive(
    Bom bom, 
    BigDecimal parentQty, 
    BigDecimal cumulativeScrap, 
    int level) {
    
    List<MaterialRequirement> requirements = new ArrayList<>();
    
    for (BomPosition pos : bom.getPositions()) {
        // Calculate net requirement
        BigDecimal scrapFactor = cumulativeScrap.add(pos.getScrapPercentage());
        BigDecimal netQty = parentQty * pos.getQuantity();
        BigDecimal plannedQty = netQty * (1 + scrapFactor);
        
        if (pos.getChildBom() == null) {
            // Leaf item: add to requirements
            MaterialRequirement mr = new MaterialRequirement();
            mr.setInventoryItem(pos.getChildBom().getParentInventoryItem());
            mr.setNetRequiredQuantity(netQty);
            mr.setPlannedRequiredQuantity(plannedQty);
            mr.setExplosionLevel(level);
            requirements.add(mr);
        } else {
            // Assembly: recurse into sub-BOM
            requirements.addAll(explodeBOMRecursive(
                pos.getChildBom(), 
                netQty, 
                scrapFactor, 
                level + 1
            ));
        }
    }
    return requirements;
}
```

#### Dependencies
- Requires WorkOrderMaterial to be generated from MaterialRequirement
- Enables inventory allocation during scheduling

---

### 4. 🔴 **Quality Management & Inspection** - CRITICAL
**Impact Level:** COMPLIANCE - Regulatory requirement; cannot be skipped for regulated industries

#### Business Problem
- WorkOrder has `INSPECTION_FAILED` status but no quality data model
- Scrapped quantities recorded but no reason captured
- No defect tracking or root cause analysis
- No inspection hold/release workflow
- No way to track first-pass yield
- Rework not linked to defects

#### Current Code Gaps
```
WorkOrder: status includes INSPECTION_FAILED but no associated inspection data
WorkOrderOperation: no inspection_required flag, no inspection history
WorkOrderMaterial: scrappedQuantity tracked but no reason
Enums: No defect code or quality status enums
```

#### Implementation Requirements
1. **Create Quality Control Entities**

   ```java
   // Quality Inspection entity
   @Entity
   public class QualityInspection {
       @Id
       private Long id;
       @ManyToOne
       private WorkOrderOperation operation;  // or work order level
       @Enumerated
       private InspectionType inspectionType;  // First Piece, In-Process, Final
       private Boolean passFailResult;  // true=pass, false=fail
       @ManyToOne
       private Employee inspector;
       private Date inspectionDate;
       private Integer sampleSize;
       private Integer defectCount;
       private String notes;
       @OneToMany(mappedBy="inspection", cascade=CascadeType.ALL)
       private List<DefectRecord> defects;
   }
   
   // Defect tracking
   @Entity
   public class DefectRecord {
       @Id
       private Long id;
       @ManyToOne
       private QualityInspection inspection;
       @ManyToOne
       private WorkOrderOperation operation;
       private String defectCode;  // e.g., "SURFACE_SCRATCH", "DIMENSION_OOS"
       private String description;
       @Enumerated
       private SeverityLevel severity;  // CRITICAL, MAJOR, MINOR
       private String rootCauseAnalysis;
       @Enumerated
       private DispositionType disposition;  // SCRAP, REWORK, USE_AS_IS, SALVAGE
       private String correctionAction;
       @ManyToOne
       private Employee createdBy;
       private Date createdDate;
   }
   ```

2. **Create Inspection Plan**
   ```sql
   CREATE TABLE inspectionPlan (
     id BIGINT PRIMARY KEY AUTO_INCREMENT,
     operationId BIGINT NOT NULL,
     inspectionType ENUM('FIRST_PIECE','IN_PROCESS','FINAL','STATISTICAL'),
     isRequired BOOLEAN DEFAULT TRUE,
     sampleSize INT,
     acceptanceCriteria VARCHAR(500),
     FOREIGN KEY (operationId) REFERENCES WorkOrderOperation(id)
   );
   ```

3. **Implement Hold/Release Workflow**
   - Operation completion waits for inspection if required
   - Inspection failed → status = INSPECTION_FAILED
   - Approval needed to release (use as-is) or rework decision

4. **Track Yield Metrics**
   - First-pass yield per operation
   - Defect rates by operation, work center, shift
   - Defect code frequency analysis
   - Scrap rate vs. rework rate

#### Database Schema Additions
```sql
CREATE TABLE qualityInspection (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  workOrderOperationId BIGINT,
  inspectionType ENUM('FIRST_PIECE','IN_PROCESS','FINAL'),
  passFailResult BOOLEAN,
  inspectorId INT,
  inspectionDate DATETIME,
  sampleSize INT,
  defectCount INT,
  notes TEXT,
  FOREIGN KEY (workOrderOperationId) REFERENCES WorkOrderOperation(id),
  FOREIGN KEY (inspectorId) REFERENCES employee(id)
);

CREATE TABLE defectRecord (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  inspectionId BIGINT NOT NULL,
  operationId BIGINT,
  defectCode VARCHAR(50),
  description VARCHAR(500),
  severity ENUM('CRITICAL','MAJOR','MINOR'),
  rootCause TEXT,
  disposition ENUM('SCRAP','REWORK','USE_AS_IS','SALVAGE'),
  correctionAction VARCHAR(500),
  createdDate DATETIME,
  FOREIGN KEY (inspectionId) REFERENCES qualityInspection(id),
  FOREIGN KEY (operationId) REFERENCES WorkOrderOperation(id)
);

CREATE TABLE defectMaster (
  id INT PRIMARY KEY AUTO_INCREMENT,
  defectCode VARCHAR(50) UNIQUE,
  description VARCHAR(500),
  category VARCHAR(100),
  defaultSeverity ENUM('CRITICAL','MAJOR','MINOR')
);
```

#### Enum Additions
```java
public enum InspectionType {
    FIRST_PIECE,      // 100% inspection of first piece
    IN_PROCESS,       // During production
    FINAL,            // Before shipment
    STATISTICAL       // Sample-based per AQL
}

public enum SeverityLevel {
    CRITICAL,  // Product cannot function, safety issue
    MAJOR,     // Affects performance/functionality
    MINOR      // Cosmetic, does not affect use
}

public enum DispositionType {
    SCRAP,      // Discard
    REWORK,     // Send back to operation
    USE_AS_IS,  // Accept despite defect
    SALVAGE     // Use for lower-grade product
}
```

#### Dependencies
- Requires WorkOrderOperation enhancement
- Requires Employee/User model for inspector tracking
- Enables Rework WorkOrder functionality (Feature #10)

---

### 5. 🔴 **Financial Tracking & Costing** - CRITICAL
**Impact Level:** FINANCIAL - Cannot calculate profitability without cost tracking

#### Business Problem
- WorkCenter has `costPerHour` but:
  - No breakdown (labor + overhead + machine rate)
  - No standard cost tracking
  - No actual cost recording per operation
  - No cost accumulation to work order
  - Scrap cost not captured
  - No variance analysis (actual vs. planned)

#### Current Code Gaps
```
WorkCenter.costPerHour: exists but not used for costing
WorkOrderOperation: no cost tracking (actual labor hours, overhead)
WorkOrderMaterial: no cost per unit or total material cost
WorkOrder: no total cost or cost tracking
```

#### Implementation Requirements
1. **Create Cost Structure Entities**

   ```java
   @Entity
   public class OperationCost {
       @Id
       private Long id;
       @ManyToOne
       private WorkOrderOperation operation;
       
       // Planned costs
       private BigDecimal plannedLaborHours;
       private BigDecimal plannedLaborRate;  // per hour
       private BigDecimal plannedLaborCost;  // hours * rate
       private BigDecimal plannedMachineCost;
       private BigDecimal plannedSetupCost;
       private BigDecimal plannedOverhead;  // calculated % of labor
       private BigDecimal plannedTotalCost;
       
       // Actual costs
       private BigDecimal actualLaborHours;
       private BigDecimal actualMachineCost;
       private BigDecimal actualSetupCost;
       private BigDecimal actualScrapCost;  // scrapped qty * unit cost
       private BigDecimal actualReworkCost;
       private BigDecimal actualOverhead;
       private BigDecimal actualTotalCost;
       
       // Variance
       private BigDecimal laborVariance;
       private BigDecimal materialVariance;
       private BigDecimal overheadVariance;
       private BigDecimal totalVariance;
       
       private Date costCalculatedDate;
   }
   
   @Entity
   public class WorkOrderCostSummary {
       @Id
       private Integer workOrderId;
       @OneToOne
       private WorkOrder workOrder;
       
       // Material costs
       private BigDecimal plannedMaterialCost;
       private BigDecimal actualMaterialCost;
       
       // Labor costs
       private BigDecimal plannedLaborCost;
       private BigDecimal actualLaborCost;
       
       // Scrap/Rework costs
       private BigDecimal scrapCost;
       private BigDecimal reworkCost;
       
       // Overhead allocation
       private BigDecimal allocatedOverhead;
       
       // Totals
       private BigDecimal plannedTotalCost;
       private BigDecimal actualTotalCost;
       private BigDecimal totalVariance;
       private BigDecimal variancePercent;
       
       private Date costCalculatedDate;
   }
   ```

2. **Implement Cost Calculation Algorithm**
   - Planned cost = standard BOM costs + standard operation costs
   - Actual cost = material cost (actual used) + actual labor + overhead
   - Scrap cost = scrapped quantity × unit cost
   - Rework cost = rework operation costs
   - Variance = Actual - Planned

3. **Create Cost Master Data**
   ```sql
   CREATE TABLE costCenter (
     id INT PRIMARY KEY AUTO_INCREMENT,
     costCenterCode VARCHAR(50) UNIQUE,
     costCenterName VARCHAR(100),
     department VARCHAR(100),
     manager VARCHAR(100)
   );
   
   CREATE TABLE overheadRate (
     id INT PRIMARY KEY AUTO_INCREMENT,
     effectiveDate DATE,
     laborOverheadPercent DECIMAL(5,2),  -- % of labor cost
     machineOverheadPercent DECIMAL(5,2),
     costCenter VARCHAR(50),
     createdDate DATETIME
   );
   
   CREATE TABLE standardCost (
     id INT PRIMARY KEY AUTO_INCREMENT,
     inventoryItemId INT NOT NULL,
     effectiveDate DATE,
     materialCost DECIMAL(15,4),
     laborHoursPerUnit DECIMAL(8,4),
     standardCostPerUnit DECIMAL(15,4),
     FOREIGN KEY (inventoryItemId) REFERENCES inventoryItem(id)
   );
   ```

4. **GL Account Linking**
   - Map operation costs to GL accounts (labor, OH, scrap)
   - Support standard cost vs. actual cost accounting
   - Generate cost journal entries at work order completion

#### Database Schema Additions
```sql
CREATE TABLE operationCost (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  workOrderOperationId BIGINT NOT NULL,
  
  -- Planned
  plannedLaborHours DECIMAL(10,2),
  plannedLaborRate DECIMAL(10,2),
  plannedLaborCost DECIMAL(15,2),
  plannedMachineCost DECIMAL(15,2),
  plannedSetupCost DECIMAL(15,2),
  plannedOverheadPercent DECIMAL(5,2),
  plannedOverheadCost DECIMAL(15,2),
  plannedTotalCost DECIMAL(15,2),
  
  -- Actual
  actualLaborHours DECIMAL(10,2),
  actualMachineCost DECIMAL(15,2),
  actualSetupCost DECIMAL(15,2),
  actualScrapCost DECIMAL(15,2),
  actualReworkCost DECIMAL(15,2),
  actualOverheadCost DECIMAL(15,2),
  actualTotalCost DECIMAL(15,2),
  
  -- Variance
  laborVariance DECIMAL(15,2),
  machineVariance DECIMAL(15,2),
  totalVariance DECIMAL(15,2),
  variancePercent DECIMAL(5,2),
  
  costCalculatedDate DATETIME,
  FOREIGN KEY (workOrderOperationId) REFERENCES WorkOrderOperation(id)
);

CREATE TABLE workOrderCostSummary (
  workOrderId INT PRIMARY KEY,
  plannedMaterialCost DECIMAL(15,2),
  actualMaterialCost DECIMAL(15,2),
  plannedLaborCost DECIMAL(15,2),
  actualLaborCost DECIMAL(15,2),
  scrapCost DECIMAL(15,2),
  reworkCost DECIMAL(15,2),
  allocatedOverhead DECIMAL(15,2),
  plannedTotalCost DECIMAL(15,2),
  actualTotalCost DECIMAL(15,2),
  totalVariance DECIMAL(15,2),
  costCalculatedDate DATETIME,
  FOREIGN KEY (workOrderId) REFERENCES workOrder(id)
);
```

#### Dependencies
- Requires OperationActuals (Feature #6) for actual hour tracking
- Requires MaterialRequirement costing
- Enables financial variance reporting

---

### 6. 🔴 **Actual Production Data Capture (MES Integration)** - CRITICAL
**Impact Level:** OPERATIONAL - Without actuals, schedule management is blind

#### Business Problem
- WorkOrderOperation has `actualStartDate/EndDate` but:
  - No operator assignment
  - No actual run time vs. setup time separation
  - No downtime tracking
  - No real-time status updates
  - Scrap reason not captured when recorded
  - OEE calculation impossible

#### Current Code Gaps
```
WorkOrderOperation:
  - actualStartDate, actualEndDate exist
  - Missing: operator, setup time, run time, downtime
  
No OperationActuals or DowntimeRecord entities
```

#### Implementation Requirements
1. **Create OperationActuals Entity**
   ```java
   @Entity
   public class OperationActuals {
       @Id
       private Long id;
       @OneToOne
       private WorkOrderOperation operation;
       
       // Operator Assignment
       @ManyToOne
       private Employee operator;
       
       // Timestamps
       private LocalDateTime actualStartTime;
       private LocalDateTime actualEndTime;
       private BigDecimal setupTimeMinutes;  // non-productive time
       private BigDecimal runTimeMinutes;    // productive time
       
       // Quantities
       private BigDecimal completedQuantity;
       private BigDecimal scrappedQuantity;
       @ManyToOne
       private DefectRecord defectReason;  // link to quality defect
       
       // Performance
       private BigDecimal cycleTimePerUnit;
       private Date recordedDate;
   }
   
   @Entity
   public class DowntimeRecord {
       @Id
       private Long id;
       @ManyToOne
       private WorkOrderOperation operation;
       @ManyToOne
       private WorkCenter workCenter;
       
       private LocalDateTime downstartTime;
       private LocalDateTime downendTime;
       private BigDecimal downtimeDurationMinutes;
       
       @Enumerated
       private DowntimeReasonType reasonType;  // PLANNED, UNPLANNED
       private String reasonDescription;
       
       @ManyToOne
       private Employee reportedBy;
       private String impactNotes;
   }
   ```

2. **Create Real-Time Status Tracking**
   ```sql
   CREATE TABLE operationStatusHistory (
     id BIGINT PRIMARY KEY AUTO_INCREMENT,
     operationId BIGINT NOT NULL,
     previousStatus ENUM('PLANNED','READY','IN_PROGRESS','COMPLETED','HOLD','CANCELLED'),
     newStatus ENUM('PLANNED','READY','IN_PROGRESS','COMPLETED','HOLD','CANCELLED'),
     statusChangeTime DATETIME,
     changedBy INT,
     reason VARCHAR(500),
     FOREIGN KEY (operationId) REFERENCES WorkOrderOperation(id)
   );
   ```

3. **Implement OEE Calculation**
   - Availability = (Planned Time - Downtime) / Planned Time
   - Performance = (Actual Output × Ideal Cycle Time) / Actual Time
   - Quality = (Good Output) / Total Output
   - OEE = Availability × Performance × Quality

4. **Add Variance Tracking**
   - Actual vs. planned time per operation
   - Downtime impact on schedule
   - Cycle time trending

#### Database Schema Additions
```sql
CREATE TABLE operationActuals (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  workOrderOperationId BIGINT NOT NULL UNIQUE,
  operatorId INT,
  actualStartTime DATETIME,
  actualEndTime DATETIME,
  setupTimeMinutes DECIMAL(10,2),
  runTimeMinutes DECIMAL(10,2),
  completedQuantity DECIMAL(15,5),
  scrappedQuantity DECIMAL(15,5),
  defectRecordId BIGINT,
  cycleTimePerUnit DECIMAL(10,4),
  recordedDate DATETIME,
  FOREIGN KEY (workOrderOperationId) REFERENCES WorkOrderOperation(id),
  FOREIGN KEY (operatorId) REFERENCES employee(id),
  FOREIGN KEY (defectRecordId) REFERENCES defectRecord(id)
);

CREATE TABLE downtimeRecord (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  workOrderOperationId BIGINT,
  workCenterId INT NOT NULL,
  downstartTime DATETIME,
  downendTime DATETIME,
  downtimeDurationMinutes DECIMAL(10,2),
  reasonType ENUM('PLANNED','UNPLANNED'),
  reasonDescription VARCHAR(500),
  reportedByEmployeeId INT,
  FOREIGN KEY (workOrderOperationId) REFERENCES WorkOrderOperation(id),
  FOREIGN KEY (workCenterId) REFERENCES workCenter(id),
  FOREIGN KEY (reportedByEmployeeId) REFERENCES employee(id)
);

CREATE TABLE operationStatusHistory (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  operationId BIGINT NOT NULL,
  previousStatus VARCHAR(50),
  newStatus VARCHAR(50),
  statusChangeTime DATETIME,
  changedByEmployeeId INT,
  reason VARCHAR(500),
  FOREIGN KEY (operationId) REFERENCES WorkOrderOperation(id),
  FOREIGN KEY (changedByEmployeeId) REFERENCES employee(id)
);
```

#### Enum Additions
```java
public enum DowntimeReasonType {
    PLANNED,      // Scheduled maintenance
    UNPLANNED,    // Equipment breakdown, material shortage, etc.
    LUNCH_BREAK,  // Shift breaks
    CHANGEOVER    // Setup for different product
}
```

#### Dependencies
- Requires Employee/Operator model
- Enables OEE dashboards and reporting
- Enables Cost calculation

---

### 7. 🟠 **Traceability & Lot/Serial Number Tracking** - HIGH
**Impact Level:** COMPLIANCE - Required for FDA, ISO, automotive recalls

#### Business Problem
- WorkOrderMaterial tracks quantity but not:
  - Which inventory lot/serial number was used
  - Serial numbers assigned to finished goods
  - Component-to-parent traceability
  - Date code/expiration tracking
  - Supplier information per consumed material

#### Current Code Gaps
```
WorkOrderMaterial: references InventoryItem (base item) but not InventoryInstance
No traceability model linking consumed materials to finished goods
```

#### Implementation Requirements
1. **Create Traceability Entities**
   ```java
   @Entity
   public class WorkOrderTraceability {
       @Id
       private Long id;
       @ManyToOne
       private WorkOrder workOrder;
       @ManyToOne
       private InventoryItem finishedGood;
       private String serialNumberAssigned;
       
       @OneToMany(mappedBy="traceability", cascade=CascadeType.ALL)
       private List<TraceabilityComponent> consumedComponents;
   }
   
   @Entity
   public class TraceabilityComponent {
       @Id
       private Long id;
       @ManyToOne
       private WorkOrderTraceability traceability;
       @ManyToOne
       private InventoryItem component;
       @ManyToOne
       private InventoryInstance consumedLot;  // specific lot/serial used
       private BigDecimal quantityConsumed;
       private Date dateCodeExpiration;
       private String supplierLotNumber;
       @ManyToOne
       private Supplier supplier;
   }
   ```

2. **Implement Serial Number Assignment**
   - Validate serial number uniqueness
   - Link finished good serial to BOM structure
   - Track date code per lot

3. **Create Traceability Queries**
   - Forward trace: "Which finished goods contain this component lot?"
   - Backward trace: "Which components went into this finished good?"
   - Supplier trace: "All products from supplier XYZ"

4. **Add Expiration Validation**
   - Block material issuance if lot expired
   - Warn if material approaching expiration
   - Track expiration in material issuance

#### Database Schema Additions
```sql
CREATE TABLE workOrderTraceability (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  workOrderId INT NOT NULL,
  finishedGoodItemId INT NOT NULL,
  serialNumberAssigned VARCHAR(100) UNIQUE,
  FOREIGN KEY (workOrderId) REFERENCES workOrder(id),
  FOREIGN KEY (finishedGoodItemId) REFERENCES inventoryItem(id)
);

CREATE TABLE traceabilityComponent (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  traceabilityId BIGINT NOT NULL,
  componentItemId INT NOT NULL,
  inventoryInstanceId BIGINT NOT NULL,  -- specific lot/serial
  quantityConsumed DECIMAL(15,5),
  dateCodeExpiration DATE,
  supplierLotNumber VARCHAR(100),
  supplierId INT,
  FOREIGN KEY (traceabilityId) REFERENCES workOrderTraceability(id),
  FOREIGN KEY (componentItemId) REFERENCES inventoryItem(id),
  FOREIGN KEY (supplierId) REFERENCES supplier(id)
);
```

#### Dependencies
- Requires Inventory instance tracking
- Requires Supplier management
- Enables recall management

---

### 8. 🟠 **Work Order Priority & Due Date Management** - HIGH
**Impact Level:** OPERATIONAL - Customer commitments depend on priority-based execution

#### Business Problem
- WorkOrder has `dueDate` but:
  - No priority field (URGENT, HIGH, NORMAL, LOW)
  - No expedite flag
  - No lateness tracking
  - No delivery promise validation
  - No priority-based scheduling override

#### Current Code Gaps
```
WorkOrder: dueDate exists but no priority
No priority enums or priority-based scheduling logic
```

#### Implementation Requirements
1. **Add Priority Fields to WorkOrder**
   ```java
   @Entity
   public class WorkOrder {
       // ...existing fields...
       
       @Enumerated(EnumType.STRING)
       private WorkOrderPriority priority = WorkOrderPriority.NORMAL;
       
       private Boolean isExpedite = false;
       private String expediteReason;
       
       @OneToOne(mappedBy="workOrder", cascade=CascadeType.ALL)
       private WorkOrderPerformance performanceTracking;
   }
   
   @Entity
   public class WorkOrderPerformance {
       @Id
       private Integer workOrderId;
       @OneToOne
       private WorkOrder workOrder;
       
       private LocalDate dueDate;
       private LocalDate actualCompletionDate;
       private Integer latenessDays;
       private Boolean isOnTime;
       private String delayReason;
   }
   ```

2. **Create Priority-Based Scheduling**
   - Override FIFO with priority-based sequencing
   - Expedite jobs get premium scheduling slots
   - Calculate schedule feasibility at due date

3. **Implement On-Time Delivery Tracking**
   - Lateness = actual completion date - due date
   - On-time delivery % per customer/product
   - Late order impact analysis

#### Database Schema Additions
```sql
ALTER TABLE workOrder ADD COLUMN priority ENUM('URGENT','HIGH','NORMAL','LOW') DEFAULT 'NORMAL';
ALTER TABLE workOrder ADD COLUMN isExpedite BOOLEAN DEFAULT FALSE;
ALTER TABLE workOrder ADD COLUMN expediteReason VARCHAR(500);

CREATE TABLE workOrderPerformance (
  workOrderId INT PRIMARY KEY,
  dueDate DATE,
  actualCompletionDate DATE,
  latenessDays INT,
  isOnTime BOOLEAN,
  delayReason VARCHAR(500),
  FOREIGN KEY (workOrderId) REFERENCES workOrder(id)
);
```

#### Enum Addition
```java
public enum WorkOrderPriority {
    URGENT,   // Rush; highest priority
    HIGH,     // Important customer/critical path
    NORMAL,   // Standard processing
    LOW       // Can be deferred
}
```

#### Dependencies
- Requires ProductionSchedule for priority-based sequencing
- Enables KPI tracking

---

### 9. 🟠 **Engineering Change Order (ECO) / BOM Change Control** - HIGH
**Impact Level:** COMPLIANCE - Regulatory requirement; uncontrolled changes create traceability breaks

#### Business Problem
- BOM has ECO fields (`ecoNumber`, `changeReason`) but:
  - No version control enforcement during work order execution
  - No notification when BOM changes mid-production
  - No snapshot of BOM as-used per work order
  - No approval workflow before BOM change
  - Cannot rollback to previous BOM

#### Current Code Gaps
```
Bom: has ecoNumber, changeReason fields but no enforcement
No BOMSnapshot or change history beyond BomHistory audit
No change notification mechanism
```

#### Implementation Requirements
1. **Create BOM Change Control**
   ```java
   @Entity
   public class BOMSnapshot {
       @Id
       private Long id;
       @ManyToOne
       private WorkOrder workOrder;
       @ManyToOne
       private Bom bomVersion;
       private Integer bomVersionSnapshot;  // BOM version# at WO creation
       
       @OneToMany(mappedBy="snapshot", cascade=CascadeType.ALL)
       private List<BOMPositionSnapshot> positions;
       
       private LocalDateTime snapshotDate;
       private String snapshotReason;  // WO creation, effective date, etc.
   }
   
   @Entity
   public class BOMChangeRequest {
       @Id
       private Long id;
       @ManyToOne
       private Bom affectedBom;
       
       private String ecoNumber;
       @Enumerated
       private ECOImpactLevel impactLevel;  // PRODUCTION, DOCUMENT_ONLY
       private String changeDescription;
       private String businessJustification;
       
       @OneToMany(mappedBy="changeRequest", cascade=CascadeType.ALL)
       private List<AffectedWorkOrder> affectedWorkOrders;
       
       @Enumerated
       private ECOApprovalStatus status;
       @ManyToOne
       private Employee requiredApprover;
       private LocalDate effectiveDate;
       
       @CreationTimestamp
       private LocalDateTime creationDate;
   }
   
   @Entity
   public class AffectedWorkOrder {
       @Id
       private Long id;
       @ManyToOne
       private BOMChangeRequest changeRequest;
       @ManyToOne
       private WorkOrder workOrder;
       
       @Enumerated
       private AffectedWorkOrderAction action;  // UPDATE, NOTIFY_ONLY, CANCEL
       private String notes;
   }
   ```

2. **Implement Change Notification**
   - Alert if BOM changes during work order execution
   - Track notification acceptance/rejection
   - Document impact on ongoing production

3. **Create Approval Workflow**
   - Production impact ECOs require approval before effectivity
   - Route to affected departments
   - Approval date controls implementation

4. **Maintain BOM Snapshots**
   - Capture BOM state at work order creation
   - Enable as-built configuration tracking
   - Support investigation of historical work orders

#### Database Schema Additions
```sql
CREATE TABLE bomSnapshot (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  workOrderId INT NOT NULL,
  bomVersionId INT NOT NULL,
  bomVersionNumber INT,
  snapshotDate DATETIME,
  snapshotReason VARCHAR(500),
  FOREIGN KEY (workOrderId) REFERENCES workOrder(id),
  FOREIGN KEY (bomVersionId) REFERENCES bom(id)
);

CREATE TABLE bomChangeRequest (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  affectedBomId INT NOT NULL,
  ecoNumber VARCHAR(50) UNIQUE,
  impactLevel ENUM('PRODUCTION','DOCUMENT_ONLY'),
  changeDescription TEXT,
  businessJustification TEXT,
  status ENUM('DRAFT','PENDING_APPROVAL','APPROVED','REJECTED','IMPLEMENTED'),
  requiredApproverId INT,
  effectiveDate DATE,
  creationDate DATETIME,
  FOREIGN KEY (affectedBomId) REFERENCES bom(id),
  FOREIGN KEY (requiredApproverId) REFERENCES employee(id)
);

CREATE TABLE affectedWorkOrder (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  changeRequestId BIGINT NOT NULL,
  workOrderId INT NOT NULL,
  action ENUM('UPDATE','NOTIFY_ONLY','CANCEL'),
  notes VARCHAR(500),
  FOREIGN KEY (changeRequestId) REFERENCES bomChangeRequest(id),
  FOREIGN KEY (workOrderId) REFERENCES workOrder(id)
);
```

#### Enum Additions
```java
public enum ECOImpactLevel {
    PRODUCTION,     // Affects active production
    DOCUMENT_ONLY   // Administrative update only
}

public enum ECOApprovalStatus {
    DRAFT,
    PENDING_APPROVAL,
    APPROVED,
    REJECTED,
    IMPLEMENTED
}

public enum AffectedWorkOrderAction {
    UPDATE,      // Update running work orders
    NOTIFY_ONLY, // Notify but no change
    CANCEL       // Cancel affected work orders
}
```

#### Dependencies
- Requires BOM versioning (already exists)
- Requires approval workflow engine
- Requires notification system

---

### 10. 🟠 **Rework & Scrap Disposition Management** - HIGH
**Impact Level:** OPERATIONAL - Rework loops and scrap accounting must be controlled

#### Business Problem
- Scrapped quantities tracked but:
  - No scrap reason/code
  - No disposition (scrap vs. rework vs. return vs. salvage)
  - No authorization workflow
  - Rework not linked to originating defect
  - Rework loop prevention not implemented

#### Current Code Gaps
```
WorkOrderMaterial: scrappedQuantity tracked but no reason
WorkOrderOperation: scrappedQuantity tracked but no reason
No ReworkWorkOrder entity linking rework to original work order
No ScrapDisposition entity
```

#### Implementation Requirements
1. **Create Rework & Scrap Entities**
   ```java
   @Entity
   public class ScrapDisposition {
       @Id
       private Long id;
       @ManyToOne
       private WorkOrderOperation sourceOperation;
       
       private BigDecimal scrapQuantity;
       private String scrapCode;  // TRIM_SCRAP, DEFECTIVE, OBSOLETE
       @ManyToOne
       private DefectRecord defectRecord;  // link to quality defect
       
       @Enumerated
       private ScrapType dispositionType;  // SCRAP, REWORK, RETURN, SALVAGE
       @Enumerated
       private ScrapApprovalStatus approvalStatus;  // PENDING, APPROVED, REJECTED
       
       @ManyToOne
       private Employee authorizedBy;
       private LocalDateTime authorizationDate;
       
       private String dispositionLocation;  // where scrap goes
       private String dispositionContractor;  // if sent to vendor
       private LocalDateTime dispositionDate;
       
       private BigDecimal scrapValue;  // estimated value of scrap
       private String notes;
   }
   
   @Entity
   public class ReworkWorkOrder {
       @Id
       private Integer reworkWorkOrderId;
       @OneToOne
       private WorkOrder reworkWorkOrder;
       
       @ManyToOne
       private WorkOrder originalWorkOrder;
       @ManyToOne
       private WorkOrderOperation sourceOperation;
       @ManyToOne
       private DefectRecord defectRecord;
       
       private BigDecimal reworkQuantity;
       private Integer reworkIterationNumber;  // 1st rework, 2nd rework, etc.
       
       @Enumerated
       private ReworkStatus reworkStatus;  // PLANNED, IN_PROGRESS, COMPLETED, SCRAPPED
       
       private LocalDateTime authorizedDate;
       @ManyToOne
       private Employee authorizedBy;
       
       private LocalDate requiredCompletionDate;
       private LocalDate actualCompletionDate;
       
       @CreationTimestamp
       private LocalDateTime creationDate;
   }
   ```

2. **Implement Scrap Authorization Workflow**
   - Scrap requires approval if above threshold
   - Authorization capture (who, when, reason)
   - Track disposition location/vendor
   - Calculate scrap value impact

3. **Create Rework Auto-Generation**
   - Defect marked for rework → auto-generate child work order
   - Link rework operation to original operation
   - Flag previous operation as requiring rework
   - Inherit BOM and routing from parent

4. **Prevent Rework Loops**
   - Limit rework iterations (e.g., max 3 times)
   - If rework scrapped again, escalate to engineering
   - Track rework costs separately

#### Database Schema Additions
```sql
CREATE TABLE scrapDisposition (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  sourceOperationId BIGINT NOT NULL,
  scrapQuantity DECIMAL(15,5),
  scrapCode VARCHAR(50),
  defectRecordId BIGINT,
  dispositionType ENUM('SCRAP','REWORK','RETURN','SALVAGE'),
  approvalStatus ENUM('PENDING','APPROVED','REJECTED'),
  authorizedByEmployeeId INT,
  authorizationDate DATETIME,
  dispositionLocation VARCHAR(100),
  dispositionContractor VARCHAR(100),
  dispositionDate DATETIME,
  scrapValue DECIMAL(15,2),
  notes VARCHAR(500),
  FOREIGN KEY (sourceOperationId) REFERENCES WorkOrderOperation(id),
  FOREIGN KEY (defectRecordId) REFERENCES defectRecord(id),
  FOREIGN KEY (authorizedByEmployeeId) REFERENCES employee(id)
);

CREATE TABLE reworkWorkOrder (
  reworkWorkOrderId INT PRIMARY KEY,
  originalWorkOrderId INT NOT NULL,
  sourceOperationId BIGINT,
  defectRecordId BIGINT,
  reworkQuantity DECIMAL(15,5),
  reworkIterationNumber INT,
  reworkStatus ENUM('PLANNED','IN_PROGRESS','COMPLETED','SCRAPPED'),
  authorizedDate DATETIME,
  authorizedByEmployeeId INT,
  requiredCompletionDate DATE,
  actualCompletionDate DATE,
  creationDate DATETIME,
  FOREIGN KEY (reworkWorkOrderId) REFERENCES workOrder(id),
  FOREIGN KEY (originalWorkOrderId) REFERENCES workOrder(id),
  FOREIGN KEY (sourceOperationId) REFERENCES WorkOrderOperation(id),
  FOREIGN KEY (defectRecordId) REFERENCES defectRecord(id),
  FOREIGN KEY (authorizedByEmployeeId) REFERENCES employee(id)
);
```

#### Enum Additions
```java
public enum ScrapType {
    SCRAP,     // Discard completely
    REWORK,    // Send back to operation
    RETURN,    // Return to supplier
    SALVAGE    // Use for lower-grade/secondary product
}

public enum ScrapApprovalStatus {
    PENDING,   // Awaiting authorization
    APPROVED,  // Authorized for disposition
    REJECTED   // Rejected; process different
}

public enum ReworkStatus {
    PLANNED,      // Rework WO created
    IN_PROGRESS,  // Rework operations started
    COMPLETED,    // Rework operations finished (pass inspection)
    SCRAPPED      // Rework failed; scrapped
}
```

#### Dependencies
- Requires QualityInspection (Feature #4) for defect linking
- Requires defect authorization approval workflow
- Enables rework cost tracking

---

### 11. 🟡 **Resource Allocation & Skill-Based Routing** - MEDIUM
**Impact Level:** OPERATIONAL - Safety and quality require skill validation

#### Business Problem
- WorkCenter has no:
  - Operator/resource pool concept
  - Skill requirement per operation
  - Operator skill level/certification tracking
  - Resource conflict detection
  - Multi-resource operation coordination

#### Current Code Gaps
```
WorkCenter: no operator pool or skill tracking
WorkOrderOperation: no resource requirement or assignment
No Employee skill or certification model
```

#### Implementation Requirements
1. **Create Operator Skill Management**
   ```java
   @Entity
   public class OperatorSkill {
       @Id
       private Long id;
       @ManyToOne
       private Employee operator;
       
       private String skillCode;  // e.g., "WELDING", "CNC_MACHINING"
       private String skillDescription;
       
       @Enumerated
       private SkillProficiencyLevel proficiencyLevel;  // NOVICE, INTERMEDIATE, EXPERT
       
       private LocalDate certificationDate;
       private LocalDate certificationExpirationDate;
       private String certifyingAuthority;
       
       private Boolean isCertified;
       @CreationTimestamp
       private LocalDateTime dateAcquired;
   }
   
   @Entity
   public class OperationSkillRequirement {
       @Id
       private Long id;
       @ManyToOne
       private RoutingOperation routingOperation;
       
       private String requiredSkillCode;
       @Enumerated
       private SkillProficiencyLevel minimumProficiencyLevel;
       
       private Boolean isMandatory = true;
   }
   
   @Entity
   public class OperatorAssignment {
       @Id
       private Long id;
       @ManyToOne
       private WorkOrderOperation workOrderOperation;
       @ManyToOne
       private Employee assignedOperator;
       
       private LocalDateTime assignmentTime;
       @ManyToOne
       private Employee assignedBy;
       
       private String notes;
       
       // Validation
       private Boolean skillsValidated;
       private String validationNotes;
   }
   ```

2. **Implement Skill Validation**
   - Check operator skills before assignment
   - Warn if skills about to expire
   - Track skill gaps
   - Support cross-training tracking

3. **Add Resource Conflict Detection**
   - Prevent double-booking of same operator
   - Coordinate multi-operator operations
   - Suggest backup operators

4. **Create Skill Development Plan**
   - Identify gaps for cross-training
   - Track certification renewal dates
   - Plan skill upgrades

#### Database Schema Additions
```sql
CREATE TABLE operatorSkill (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  employeeId INT NOT NULL,
  skillCode VARCHAR(50),
  skillDescription VARCHAR(200),
  proficiencyLevel ENUM('NOVICE','INTERMEDIATE','EXPERT'),
  certificationDate DATE,
  certificationExpirationDate DATE,
  certifyingAuthority VARCHAR(100),
  isCertified BOOLEAN,
  dateAcquired DATETIME,
  FOREIGN KEY (employeeId) REFERENCES employee(id)
);

CREATE TABLE operationSkillRequirement (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  routingOperationId BIGINT NOT NULL,
  requiredSkillCode VARCHAR(50),
  minimumProficiencyLevel ENUM('NOVICE','INTERMEDIATE','EXPERT'),
  isMandatory BOOLEAN DEFAULT TRUE,
  FOREIGN KEY (routingOperationId) REFERENCES routingOperation(id)
);

CREATE TABLE operatorAssignment (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  workOrderOperationId BIGINT NOT NULL,
  assignedEmployeeId INT NOT NULL,
  assignmentTime DATETIME,
  assignedByEmployeeId INT,
  skillsValidated BOOLEAN,
  validationNotes VARCHAR(500),
  notes VARCHAR(500),
  FOREIGN KEY (workOrderOperationId) REFERENCES WorkOrderOperation(id),
  FOREIGN KEY (assignedEmployeeId) REFERENCES employee(id),
  FOREIGN KEY (assignedByEmployeeId) REFERENCES employee(id)
);
```

#### Enum Addition
```java
public enum SkillProficiencyLevel {
    NOVICE,        // Learning
    INTERMEDIATE,  // Competent
    EXPERT         // Trainer/expert level
}
```

#### Dependencies
- Requires Employee model enhancement
- Requires OperatorAssignment workflow
- Enables compliance with safety regulations

---

### 12. 🟡 **Work Order Hold/Release & Status Blocking Rules** - MEDIUM
**Impact Level:** OPERATIONAL - Prevents premature completion and ensures sequencing

#### Business Problem
- WorkOrder status includes `HOLD` but:
  - No hold reason tracking
  - No hold-blocking rules (operations complete while parent on hold)
  - No automatic release trigger logic
  - No cascading holds to child work orders
  - No approval workflow for holds

#### Current Code Gaps
```
WorkOrder.workOrderStatus includes HOLD but no supporting entities
No HoldReason or WorkOrderHold model
No validation preventing state transitions
```

#### Implementation Requirements
1. **Create Hold Management Entities**
   ```java
   @Entity
   public class HoldReason {
       @Id
       private String reasonCode;
       private String description;
       private Boolean requiresApproval = false;
       private String requiredApprovingRole;
       @CreationTimestamp
       private LocalDateTime createdDate;
   }
   
   @Entity
   public class WorkOrderHold {
       @Id
       private Long id;
       @ManyToOne
       private WorkOrder workOrder;
       
       @ManyToOne
       private HoldReason holdReason;
       private String additionalNotes;
       
       private LocalDateTime holdInitiatedDate;
       @ManyToOne
       private Employee holdInitiatedBy;
       
       private Boolean requiresApproval;
       @ManyToOne
       private Employee approvalRequiredFrom;
       private LocalDateTime approvalDate;
       @ManyToOne
       private Employee approvedBy;
       
       private LocalDateTime holdReleaseDate;
       @ManyToOne
       private Employee releasedBy;
       private String releaseNotes;
       
       @OneToMany(mappedBy="workOrderHold", cascade=CascadeType.ALL)
       private List<OperationHold> affectedOperations;
   }
   
   @Entity
   public class OperationHold {
       @Id
       private Long id;
       @ManyToOne
       private WorkOrderHold workOrderHold;
       @ManyToOne
       private WorkOrderOperation operation;
       
       private LocalDateTime operationHoldDate;
       private Boolean operationBlockedFromCompletion = true;
   }
   ```

2. **Implement Status Transition Rules**
   - Cannot complete operation if parent work order on HOLD
   - Cannot release work order if child operations on HOLD
   - Track hold/release history

3. **Create Automatic Release Logic**
   - Material received → release hold
   - Quality approval → release hold
   - Equipment ready → release hold
   - Prerequisite operation completed → release hold

4. **Add Cascading Hold Logic**
   - Parent on HOLD → cascade to children
   - Child on HOLD → parent cannot progress past certain point

#### Database Schema Additions
```sql
CREATE TABLE holdReason (
  reasonCode VARCHAR(50) PRIMARY KEY,
  description VARCHAR(500),
  requiresApproval BOOLEAN DEFAULT FALSE,
  requiredApprovingRole VARCHAR(50),
  createdDate DATETIME
);

CREATE TABLE workOrderHold (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  workOrderId INT NOT NULL,
  holdReasonCode VARCHAR(50) NOT NULL,
  additionalNotes VARCHAR(500),
  holdInitiatedDate DATETIME,
  holdInitiatedByEmployeeId INT,
  requiresApproval BOOLEAN,
  approvalRequiredFromEmployeeId INT,
  approvalDate DATETIME,
  approvedByEmployeeId INT,
  holdReleaseDate DATETIME,
  releasedByEmployeeId INT,
  releaseNotes VARCHAR(500),
  FOREIGN KEY (workOrderId) REFERENCES workOrder(id),
  FOREIGN KEY (holdReasonCode) REFERENCES holdReason(reasonCode),
  FOREIGN KEY (holdInitiatedByEmployeeId) REFERENCES employee(id),
  FOREIGN KEY (approvedByEmployeeId) REFERENCES employee(id),
  FOREIGN KEY (releasedByEmployeeId) REFERENCES employee(id)
);

CREATE TABLE operationHold (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  workOrderHoldId BIGINT NOT NULL,
  workOrderOperationId BIGINT NOT NULL,
  operationHoldDate DATETIME,
  operationBlockedFromCompletion BOOLEAN DEFAULT TRUE,
  FOREIGN KEY (workOrderHoldId) REFERENCES workOrderHold(id),
  FOREIGN KEY (workOrderOperationId) REFERENCES WorkOrderOperation(id)
);

-- Predefined hold reasons
INSERT INTO holdReason VALUES 
  ('MATERIAL_WAITING', 'Waiting for material availability', FALSE, NULL),
  ('QUALITY_HOLD', 'Quality inspection pending', TRUE, 'QUALITY_MANAGER'),
  ('CUSTOMER_REQUEST', 'Customer requested hold', TRUE, 'SALES_MANAGER'),
  ('EQUIPMENT_DOWN', 'Work center equipment down', FALSE, NULL),
  ('ENGINEERING_REVIEW', 'Awaiting engineering review', TRUE, 'ENGINEERING_MANAGER'),
  ('APPROVAL_PENDING', 'Awaiting approval', TRUE, 'MANAGER');
```

#### Enum Enhancement
Already have `HOLD` in WorkOrderStatus but need blocking rules

#### Dependencies
- Requires Employee/Approval workflow
- Requires audit trail for hold history
- Enables production control

---

## Implementation Roadmap

### Phase 1: Scheduling & Capacity (Weeks 1-4) - CRITICAL PATH
1. **Week 1-2:** ProductionSchedule + OperationDependency entities and schema
2. **Week 2-3:** WorkCenterCalendar + WorkCenterLoad entities
3. **Week 3-4:** Basic scheduling algorithm (forward pass)
4. **Week 4:** Capacity validation logic

**Outcome:** Basic production scheduling with capacity awareness

---

### Phase 2: Quality & Financial (Weeks 5-8) - COMPLIANCE & VISIBILITY
1. **Week 5-6:** QualityInspection + DefectRecord + InspectionPlan entities
2. **Week 6-7:** OperationCost + WorkOrderCostSummary entities
3. **Week 7-8:** Cost calculation algorithm + GL integration

**Outcome:** Quality management and cost tracking foundation

---

### Phase 3: Production Execution (Weeks 9-12) - OPERATIONAL
1. **Week 9-10:** OperationActuals + DowntimeRecord + OperationStatusHistory
2. **Week 10-11:** Multi-level BOM explosion algorithm
3. **Week 11-12:** Actual vs. planned variance tracking + OEE calculation

**Outcome:** Real-time production visibility and execution

---

### Phase 4: Traceability & Control (Weeks 13-16) - COMPLIANCE & REWORK
1. **Week 13:** WorkOrderTraceability + TraceabilityComponent
2. **Week 14:** BOMSnapshot + BOMChangeRequest (ECO control)
3. **Week 15:** ScrapDisposition + ReworkWorkOrder entities
4. **Week 16:** Hold management + Resource allocation

**Outcome:** Full traceability, compliance, and rework management

---

### Phase 5: Advanced Features (Weeks 17-20) - OPTIMIZATION
1. **Week 17-18:** Skill-based routing + operator assignments
2. **Week 18-19:** Priority-based scheduling
3. **Week 19-20:** Dashboard development + reporting

**Outcome:** Optimized production scheduling with resource management

---

## Critical Success Factors

1. **Database Performance:** Index work center load and dates for scheduling queries
2. **API Design:** RESTful endpoints for each feature enabling MES integration
3. **Audit Trail:** Every entity should have creation/modification tracking
4. **Role-Based Access:** Quality, finance, production managers need different visibility
5. **Reporting:** Dashboards for OEE, on-time delivery, cost variance, yield rates

---

## Regulatory Compliance Alignment

- **FDA/Medical:** Features #4, #7, #9, #10 are critical
- **ISO Manufacturing:** Features #1-6 are compliance minimum
- **Automotive (IATF):** All 12 features needed for full compliance
- **Traceability:** Features #7, #9, #10 mandatory

---

## Expected Business Impact

| Feature | Impact | Timeline |
|---------|--------|----------|
| Scheduling | 20-30% improvement in schedule adherence | Phase 1 |
| Quality Mgmt | 80%+ first-pass yield visibility | Phase 2 |
| Costing | Accurate product cost within 5% | Phase 2 |
| Real-time Data | OEE visibility (target: >70%) | Phase 3 |
| Traceability | Recall capability <4 hours | Phase 4 |
| Resource Mgmt | 25% improvement in operator utilization | Phase 5 |

---

**Recommendation:** Begin Phase 1 (Scheduling) immediately as it's the foundation for all other features.
