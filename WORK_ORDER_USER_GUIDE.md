# Work Order Management System - User Guide

**Version 1.0** | **Last Updated: February 2026**

---

## Table of Contents

1. [Overview](#overview)
2. [What is a Work Order?](#what-is-a-work-order)
3. [Work Order Lifecycle](#work-order-lifecycle)
4. [Getting Started](#getting-started)
5. [Key Concepts](#key-concepts)
6. [How to Create a Work Order](#how-to-create-a-work-order)
7. [How to Manage Work Orders](#how-to-manage-work-orders)
8. [Work Order Operations](#work-order-operations)
9. [Material Management](#material-management)
10. [Tracking and Monitoring](#tracking-and-monitoring)
11. [Troubleshooting](#troubleshooting)
12. [Best Practices](#best-practices)

---

## Overview

The **Work Order Management System** is an integrated production planning and execution module that enables manufacturers to efficiently plan, track, and execute production activities. This system automatically breaks down complex manufacturing orders into manageable operations and tracks material consumption in real-time.

### Key Benefits
- **Automated Planning**: Automatically explodes Bills of Material (BOM) and Routing into work steps
- **Real-time Tracking**: Monitor production progress at operation and material levels
- **Audit Trail**: Complete history of all changes and activities
- **Quality Control**: Built-in validations ensure data integrity
- **Flexibility**: Adjust quantities and dates before production begins

---

## What is a Work Order?

A **Work Order** is a manufacturing instruction that defines:
- **WHAT** to produce (the product via BOM)
- **HOW** to produce it (the routing/operations)
- **HOW MUCH** to produce (planned quantity)
- **WHEN** to produce it (planned dates)
- **WHERE** to produce it (work center/facility)

### Work Order Components

```
Work Order (WO-1234)
├── Bill of Material (BOM)
│   └── Required Materials
│       ├── Component A (Qty: 50)
│       ├── Component B (Qty: 30)
│       └── Component C (Qty: 15)
├── Routing (Production Steps)
│   ├── Operation 1: Cutting (2 hours)
│   ├── Operation 2: Assembly (3 hours)
│   └── Operation 3: Testing (1.5 hours)
└── Metadata
    ├── Status
    ├── Dates
    └── Quantities
```

---

## Work Order Lifecycle

Every Work Order progresses through distinct states. Understanding this flow ensures smooth production execution:

```
┌─────────┐     ┌──────────┐     ┌──────────────┐     ┌──────────┐
│ CREATED │────→│ RELEASED │────→│ IN_PROGRESS  │────→│COMPLETED │
└─────────┘     └──────────┘     └──────────────┘     └──────────┘
     │                                   │                   │
     │                                   ↓                   │
     │                            ┌──────────────┐          │
     └───────────────────────────→│ CANCELLED    │←─────────┘
                                  └──────────────┘

                            Final State: CLOSED
                           (After completion)
```

### Status Definitions

| Status | Meaning | What You Can Do |
|--------|---------|-----------------|
| **CREATED** | Work order has been created but not yet authorized | Edit quantities, dates, materials; Update sources |
| **RELEASED** | Work order is authorized and ready for production | Start operations; Track progress; View materials |
| **IN_PROGRESS** | Production has begun | Complete operations; Record completions; Monitor progress |
| **COMPLETED** | All operations done, all materials issued | Close the work order |
| **CLOSED** | Work order is finalized and archived | View history; Generate reports |
| **CANCELLED** | Work order was cancelled before completion | Cannot be restarted (must create new WO) |

---

## Getting Started

### System Requirements
- Internet browser (Chrome, Firefox, Safari, Edge)
- Appropriate user permissions
- Access to inventory and production modules

### Logging In
1. Navigate to NextGen Manager application
2. Enter your credentials
3. Select "Production" → "Work Orders"

### Navigation
- **View All Work Orders**: Work Orders dashboard
- **Create New**: "+ Create Work Order" button
- **Search**: Use the search bar (searches by WO#, BOM, Sales Order, Work Center)
- **Filter**: Use status, date, or custom filters

---

## Key Concepts

### Bill of Material (BOM)
A detailed list of all components required to manufacture a product, including:
- Component identification
- Required quantity per unit
- Unit of measure
- Optional sub-assemblies

**Example**: To make a "Assembled Widget", you need:
- 2 × Widget Base
- 5 × Widget Fasteners
- 1 × Widget Assembly

### Routing
The production sequence and work centers required to transform raw materials into finished goods:
- Sequential operations
- Work center assignments
- Standard processing times
- Quality checkpoints

**Example**: Manufacturing "Assembled Widget" requires:
1. **Cutting** at CNC Center (2 hrs)
2. **Assembly** at Assembly Station (3 hrs)
3. **Testing** at QA Station (1.5 hrs)

### Planned Quantity vs. Completed Quantity
- **Planned Quantity**: How much you intend to produce
- **Completed Quantity**: How much has actually been completed
- **Progress %**: (Completed ÷ Planned) × 100

### Work Centers
Physical or logical production areas where operations are performed:
- CNC Machining Center
- Assembly Station
- Testing Lab
- Packaging Area

### Source Type
Indicates where the work order originated:
- **Sales Order**: Customer order requiring production
- **Parent Work Order**: Sub-order from another work order
- **Manual**: Manually created for various reasons

---

## How to Create a Work Order

### Step-by-Step Process

#### Step 1: Navigate to Work Order Creation
```
Menu: Production → Work Orders → Create New Work Order
```

#### Step 2: Provide Mandatory Information
You MUST provide:
- **Bill of Material (BOM)**: Select the product to manufacture
  - Searching by product name or code
  - System validates BOM exists in database
  
- **Planned Quantity**: How many units to produce
  - Must be greater than zero
  - Use decimal values if needed (e.g., 10.5 units)
  - Example: Enter "100" to produce 100 units

#### Step 3: Provide Optional Information
- **Sales Order**: Link to customer order (if applicable)
- **Work Center**: Assign production facility
  - Overrides default from routing if needed
  - Example: Route to specific plant location
  
- **Dates**:
  - **Due Date**: When customer needs the products
  - **Planned Start Date**: When production should begin
  - **Planned End Date**: When production should complete
  
- **Remarks**: Internal notes about the order
  - Special instructions
  - Quality notes
  - Customer requests

#### Step 4: Set Source Type
Define where this order originated:

**If Sales Order Based:**
- Select "Sales Order" source type
- Link to specific customer order
- System locks this relationship

**If Parent Work Order Based:**
- Select "Parent Work Order" source type
- Link to parent manufacturing order
- Creates hierarchical traceability

**If Manual:**
- Select "Manual" for internal production

#### Step 5: Review and Create

The system will:
1. **Validate** all mandatory fields
2. **Auto-Generate** Work Order Number (WO-XXXXX)
3. **Explode BOM** into material requirements
4. **Create Operations** from routing sequence
5. **Set Initial Status** to CREATED
6. **Record Audit Entry** for tracking

**Example Output:**
```
Work Order: WO-10047
├── Product: Assembled Widget
├── Planned Quantity: 100 units
├── Status: CREATED
├── Auto-Generated Materials: 3 line items
├── Auto-Generated Operations: 3 steps
└── Ready for: Editing or Release
```

### Validation Rules

The system will **REJECT** creation if:
- ❌ No BOM is provided (mandatory)
- ❌ Planned quantity is zero or negative
- ❌ BOM doesn't exist in system
- ❌ Planned quantity is non-numeric

### Auto-Generated Components

When you create a work order, the system automatically:

**1. Calculates Material Requirements**
```
For each BOM line item:
  Required Qty = BOM Line Quantity × Planned Quantity

Example:
  BOM says: 5 fasteners per unit
  You planned: 100 units
  System calculates: 500 fasteners required
```

**2. Creates Operations from Routing**
```
For each routing operation:
  Operation Status = PLANNED
  Planned Qty = Your ordered quantity
  Work Center = From routing definition
  Sequence = From routing order

Example:
  Operation 1: Cutting (Seq 1)
  Operation 2: Assembly (Seq 2)
  Operation 3: Testing (Seq 3)
```

---

## How to Manage Work Orders

### Updating a Work Order

**When Can You Edit?** 
- ✅ Status = CREATED (before release)
- ❌ All other statuses are locked

**What Can You Change?**

| Field | Can Change | Notes |
|-------|-----------|-------|
| BOM | ❌ NO | Set at creation, cannot change |
| Routing | ❌ NO | Derived from BOM, cannot change |
| Planned Quantity | ✅ YES | Only if no operations/materials issued |
| Work Center | ✅ YES | Override production facility |
| Dates | ✅ YES | Start/End dates can be adjusted |
| Remarks | ✅ YES | Add special notes |
| Source Type | ✅ YES | Change where order originated |
| Sales Order Link | ✅ YES | Only for Sales Order source type |

**How to Update:**
```
1. Click on Work Order number to open
2. Click "Edit" button
3. Modify allowed fields
4. Click "Save"
5. System records change in audit log
```

**Quantity Change Restrictions:**
When updating planned quantity, system checks:
- Are any operations already started? → NO (prevents change)
- Are any materials already issued? → NO (prevents change)
- If both clear, recalculates all material & operation quantities proportionally

**Example: Quantity Increase**
```
Original: 100 units
  Material A: 100 × 5 = 500 needed
  Material B: 100 × 3 = 300 needed

Updated: 150 units (increase of 50%)
  Material A: 150 × 5 = 750 needed (↑250)
  Material B: 150 × 3 = 450 needed (↑150)
```

### Releasing a Work Order

**Why Release?** 
Once released, the work order cannot be edited—it's locked for production execution.

**What Happens at Release?**
1. Status changes from CREATED → RELEASED
2. First operation automatically set to READY
3. All other operations stay PLANNED
4. Materials become visible to warehouse
5. System records release in audit log

**Pre-Release Validations:**
The system checks that work order is complete:
- ✓ BOM exists and is valid
- ✓ Routing exists and is valid  
- ✓ Materials were auto-generated (not empty)
- ✓ Operations were auto-generated (not empty)

**Release Process:**
```
Work Order Detail → Click "Release" → Confirm → Status: RELEASED
```

**What If Release Fails?**
| Error | Cause | Solution |
|-------|-------|----------|
| "BOM and Routing are mandatory" | Missing BOM or routing | Re-create work order with valid BOM |
| "Must have materials before release" | BOM didn't generate materials | Verify BOM has line items |
| "Must have operations before release" | Routing didn't generate operations | Verify routing has operations |

### Cancelling a Work Order

**When to Cancel?**
- Order is no longer needed
- Customer cancelled their request
- Duplicate work order created
- Production not yet started

**When CAN'T You Cancel?**
- ❌ Work order is already COMPLETED
- ❌ Work order is already CLOSED
- ❌ An operation is currently IN_PROGRESS

**Cancel Process:**
```
Work Order Detail → Click "Cancel" → Confirm Reason → Status: CANCELLED
```

**What Happens?**
- Work order status → CANCELLED
- All non-completed operations → CANCELLED
- Completed operations → stay COMPLETED (history preserved)
- System records cancellation in audit log

### Deleting a Work Order (Soft Delete)

**What is Soft Delete?** 
Records are marked as deleted but remain in database for audit purposes. No permanent data loss.

**When Can You Delete?**
- ✅ Status = CREATED (never started)
- ✅ Status = CANCELLED (already stopped)
- ❌ All other statuses locked

**Delete Process:**
```
Work Order Detail → Click "Delete" → Enter Reason → Confirm
```

**What Gets Deleted?**
- Work order header
- All associated materials
- All associated operations
- All records marked with deletion timestamp

**Audit Trail Preserved:**
Even deleted work orders remain queryable for:
- Historical analysis
- Traceability
- Compliance reporting

---

## Work Order Operations

### Understanding Operations

An **Operation** is a distinct production step within a work order:

```
Work Order: WO-10047
├── Operation 1: Cutting
│   ├── Sequence: 1 (must complete first)
│   ├── Work Center: CNC Machine
│   ├── Planned Qty: 100 units
│   ├── Status: PLANNED → READY → IN_PROGRESS → COMPLETED
│   └── Times: Actual Start/End recorded
│
├── Operation 2: Assembly  
│   ├── Sequence: 2 (after Operation 1 complete)
│   ├── Work Center: Assembly Station
│   ├── Planned Qty: 100 units
│   └── Status: PLANNED
│
└── Operation 3: Testing
    ├── Sequence: 3 (after Operation 2 complete)
    ├── Work Center: QA Lab
    ├── Planned Qty: 100 units
    └── Status: PLANNED
```

### Operation Statuses

| Status | Meaning | Action |
|--------|---------|--------|
| **PLANNED** | Operation is awaiting execution | Wait for release |
| **READY** | Operation is next in sequence | Can be started |
| **IN_PROGRESS** | Currently being executed | Record completions |
| **COMPLETED** | Finished and passed QA | Next operation unlocks |
| **CANCELLED** | Cancelled before completion | Cannot be restarted |

### Starting an Operation

**Prerequisites:**
- ✓ Work order must be RELEASED or IN_PROGRESS
- ✓ Operation must be READY
- ✓ Previous operation must be COMPLETED
- ✓ No other operation IN_PROGRESS (sequential execution)

**How to Start:**
```
1. Open Work Order Detail
2. Navigate to "Operations" tab
3. Find operation with status "READY"
4. Click "Start Operation"
5. System records actual start time
6. Operation status → IN_PROGRESS
7. Work order status → IN_PROGRESS (if first operation)
```

**Sequence Enforcement:**
The system enforces strict operation sequence:
```
Operation 1: CUTTING
├─ Must be COMPLETED before starting Operation 2
│
└─→ Operation 2: ASSEMBLY  
    ├─ Must be COMPLETED before starting Operation 3
    │
    └─→ Operation 3: TESTING
        └─ Last operation; unlocks work order completion
```

### Completing an Operation

**What You Report:**
- How many units were successfully completed
- System automatically records completion time

**How to Complete:**
```
1. Open Work Order Detail
2. Navigate to "Operations" tab
3. Find operation with status "IN_PROGRESS"
4. Enter "Completed Quantity" (actual units completed)
5. Click "Complete Operation"
6. System validates quantity
7. Operation status → COMPLETED
8. Next operation auto-set to READY
9. Work order progress updated
```

**Quantity Validation:**
```
Planned Qty: 100 units
Completed Qty: ?

Options:
  ✓ Complete 100 (exactly as planned)
  ✓ Complete 95 (less than planned - acceptable)
  ✓ Complete 85 (scrap/loss of 15 units - acceptable)
  
  With Over-Completion Permission:
  ✓ Complete 105 (5% overage - if allowed)
  
  ✗ Cannot complete 0 (must be > 0)
  ✗ Cannot complete negative values
```

**Automatic Effects:**
When operation completes:
1. ✓ Operation status → COMPLETED
2. ✓ Next operation status → READY (if exists)
3. ✓ Work order completed quantity updated
4. ✓ Work order progress % updated
5. ✓ Audit entry recorded

**Example Completion Cascade:**
```
Time: 10:00 AM
  Operation 1 Completed: 100 units
  Status: Cutting → COMPLETED
  Effect: Assembly → READY (now can start)

Time: 2:30 PM  
  Operation 2 Completed: 100 units
  Status: Assembly → COMPLETED
  Effect: Testing → READY (now can start)

Time: 4:00 PM
  Operation 3 Completed: 100 units
  Status: Testing → COMPLETED
  Effect: All operations COMPLETED
         Work Order can now be COMPLETED
```

---

## Material Management

### Understanding Material Requirements

When you create a work order, materials are automatically calculated:

```
BOM Structure:
  Product A requires:
    - Material X: 5 units per product
    - Material Y: 3 units per product
    - Material Z: 2 units per product

Work Order: 100 units of Product A
  Material X: 100 × 5 = 500 units required
  Material Y: 100 × 3 = 300 units required
  Material Z: 100 × 2 = 200 units required
```

### Material Issue Statuses

| Status | Meaning | Next Action |
|--------|---------|-------------|
| **NOT_ISSUED** | Not yet released to production | Issue from warehouse |
| **ISSUED** | Released to production floor | Monitor consumption |
| **SCRAPPED** | Lost/damaged during production | Adjust inventory |

### How Material Management Works

#### 1. Material Creation (Automatic)
```
When you CREATE a work order:
  ✓ System reads BOM
  ✓ For each BOM line:
    - Creates work order material line
    - Calculates required quantity
    - Sets status: NOT_ISSUED
    - Sets issued quantity: 0
```

#### 2. Material Issue (Manual/Warehouse)
```
When warehouse ISSUES materials:
  ✓ Navigate to Work Order Materials
  ✓ Select material to issue
  ✓ Confirm quantity (usually full required qty)
  ✓ Record issue in system
  ✓ Status changes: NOT_ISSUED → ISSUED
  ✓ Material becomes unavailable in general inventory
```

#### 3. Material Tracking During Production
```
As operations complete:
  ✓ Monitor material consumption
  ✓ Record scrap/loss
  ✓ Update consumption vs. requirement
  ✓ Alert if consumption exceeds planned
```

### Material Requirements and Quantity Changes

**If You Change Planned Quantity:**

```
Original: 100 units
  Material A required: 100 × 5 = 500 units

Updated to: 150 units (increase 50%)
  Material A recalculated: 150 × 5 = 750 units
  (system multiplies all material quantities by ratio: 1.5)

System checks first:
  ✓ No materials already issued? → Allows change
  ✗ Materials already issued? → Blocks change
```

### Material Completion Requirements

**To Complete Work Order:**
- ✓ All materials must be ISSUED
- ✓ All operations must be COMPLETED
- ✗ Cannot complete if materials marked NOT_ISSUED

---

## Tracking and Monitoring

### Work Order Dashboard

**Key Metrics Displayed:**
```
Work Order: WO-10047
├─ Status: IN_PROGRESS
├─ Product: Assembled Widget
├─ Planned Quantity: 100 units
├─ Completed Quantity: 67 units  
├─ Progress: 67%
├─ Planned Dates:
│  ├─ Start: Feb 10, 2026
│  ├─ End: Feb 15, 2026
│  ├─ Due: Feb 20, 2026
├─ Actual Dates:
│  ├─ Start: Feb 10, 2026 (on time!)
│  ├─ End: (in progress)
├─ Materials: 3 required, 3 issued
├─ Operations: 3 total
│  ├─ 1 COMPLETED
│  ├─ 1 IN_PROGRESS
│  └─ 1 PLANNED
└─ Source: Sales Order SO-5432
```

### Progress Tracking

**Real-Time Progress %:**
```
Progress = (Completed Quantity ÷ Planned Quantity) × 100

Work Order WO-10047:
  Planned: 100 units
  Completed: 30 units
  Progress: (30 ÷ 100) × 100 = 30% ✓

As operations complete:
  Completed: 60 units
  Progress: (60 ÷ 100) × 100 = 60% ✓

Final completion:
  Completed: 100 units
  Progress: (100 ÷ 100) × 100 = 100% ✓
```

### Monitoring Operations

**Operation Detail View:**
```
Operation 1: Cutting
├─ Sequence: 1
├─ Status: COMPLETED ✓
├─ Work Center: CNC Machine
├─ Planned Qty: 100 units
├─ Completed Qty: 100 units (100%)
├─ Actual Start: Feb 10, 2026 @ 8:00 AM
├─ Actual End: Feb 10, 2026 @ 10:30 AM
└─ Duration: 2.5 hours

Operation 2: Assembly
├─ Sequence: 2
├─ Status: IN_PROGRESS ⏱
├─ Work Center: Assembly Station
├─ Planned Qty: 100 units
├─ Completed Qty: 67 units (67%)
├─ Actual Start: Feb 10, 2026 @ 11:00 AM
├─ Actual End: (still in progress)
└─ Duration: 2.5 hours so far

Operation 3: Testing
├─ Sequence: 3
├─ Status: PLANNED (locked)
├─ Work Center: QA Lab
├─ Planned Qty: 100 units
├─ Completed Qty: 0 units
└─ Ready after: Operation 2 completes
```

### Search and Filtering

**Search by:**
- Work Order Number (WO-10047)
- Product Name (Assembled Widget)
- Sales Order (SO-5432)
- Work Center (Assembly Station)

**Filter by:**
- Status (Created, Released, In Progress, etc.)
- Date Range (created, due, completed)
- Quantity Completed (100%, 50-75%, 0%)
- Source Type (Sales Order, Manual, etc.)

**Example Search:**
```
Search Term: "Widget"
Results Include:
  ✓ Work orders with BOM "Assembled Widget"
  ✓ Work orders with product code containing "Widget"
  ✓ Linked sales orders with "Widget" in description
```

### Audit History

**Complete Activity Log:**
Every change is recorded with:
- What changed (field name)
- Who made the change (user)
- When it changed (timestamp)
- Old value → New value
- Reason for change

**Audit Trail Example:**
```
Work Order WO-10047 Audit Log:

2026-02-10 08:00:00 | USER: jsmith | CREATED
  Event: WorkOrder created
  Planned Qty: 100 units

2026-02-10 09:30:00 | USER: jsmith | UPDATED  
  Field: Planned Start Date
  From: Feb 10, 08:00 → To: Feb 10, 10:00
  Reason: Adjusted for material arrival delay

2026-02-10 10:00:00 | USER: mwilson | RELEASED
  Status: CREATED → RELEASED

2026-02-10 11:00:00 | USER: dchen | OPERATION_STARTED
  Operation: 1 - Cutting

2026-02-10 13:30:00 | USER: dchen | OPERATION_COMPLETED
  Operation: 1 - Cutting
  Completed Qty: 100 units
```

---

## Troubleshooting

### Common Issues and Solutions

#### Issue 1: "Cannot Create Work Order"

**Error: "BOM is required to create Work Order"**
- **Cause**: You didn't select a BOM
- **Solution**: 
  1. Click BOM dropdown
  2. Type product name to search
  3. Select valid BOM from list
  4. Try again

**Error: "Planned quantity must be greater than zero"**
- **Cause**: Quantity is 0, negative, or blank
- **Solution**:
  1. Enter quantity field
  2. Clear current value
  3. Enter number greater than 0 (e.g., 10)
  4. Try again

#### Issue 2: "Cannot Release Work Order"

**Error: "BOM and Routing are mandatory to release WorkOrder"**
- **Cause**: Work order missing BOM or routing definition
- **Solution**:
  1. Verify BOM exists in system
  2. Verify BOM has routing assigned
  3. Contact administrator if routing missing
  4. Create new work order with valid BOM

**Error: "WorkOrder must have materials before release"**
- **Cause**: BOM didn't generate materials (empty BOM)
- **Solution**:
  1. Check BOM definition
  2. Verify BOM has line items (components)
  3. Use different BOM if current one empty
  4. Contact product data team to fix BOM

**Error: "WorkOrder must have operations before release"**
- **Cause**: Routing didn't generate operations (empty routing)
- **Solution**:
  1. Check routing definition
  2. Verify routing has operation steps
  3. Use different routing if current one empty
  4. Contact production planning to define routing

#### Issue 3: "Cannot Update Work Order"

**Error: "WorkOrder can only be updated in CREATED status"**
- **Cause**: Work order already released or in progress
- **Solution**:
  1. Changes only allowed in CREATED status
  2. If critical, cancel work order and create new one
  3. Document reason for cancellation
  4. Contact supervisor for approval

**Error: "BOM cannot be changed after WorkOrder creation"**
- **Cause**: Attempted to change product
- **Solution**:
  1. BOM is locked at creation (by design)
  2. Cancel this work order if wrong product
  3. Create new work order with correct BOM
  4. Link to same sales order if needed

**Error: "Planned quantity cannot be changed when materials have been issued"**
- **Cause**: Warehouse already issued materials
- **Solution**:
  1. Materials are locked once issued
  2. Cancel work order if significant change needed
  3. Create new work order with correct quantity
  4. Coordinate with warehouse to reverse issue if possible

#### Issue 4: "Cannot Start Operation"

**Error: "Only READY operations can be started"**
- **Cause**: Operation is not yet ready
- **Solution**:
  1. Check previous operation status
  2. Complete previous operation first
  3. System automatically sets next to READY
  4. Then you can start this operation

**Error: "Previous operation must be COMPLETED before starting this operation"**
- **Cause**: Operations must be done sequentially
- **Solution**:
  1. Complete operation 1 first
  2. Then operation 2 becomes READY
  3. Then you can start operation 2
  4. Continue in sequence

**Error: "Another operation is already in progress for this WorkOrder"**
- **Cause**: Can only run one operation at a time
- **Solution**:
  1. Complete current in-progress operation
  2. System will unlock next operation
  3. Then start next operation
  4. This ensures sequence integrity

#### Issue 5: "Cannot Complete Operation"

**Error: "Only IN_PROGRESS operations can be completed"**
- **Cause**: Operation not currently running
- **Solution**:
  1. Click "Start Operation" first
  2. Wait until operation is IN_PROGRESS
  3. Then complete it

**Error: "Completed quantity exceeds planned quantity"**
- **Cause**: Over-completion not allowed for this operation
- **Solution**:
  1. Enter quantity ≤ planned quantity
  2. OR request over-completion permission
  3. Contact supervisor for approval
  4. Flag quality issues if over-scrap

#### Issue 6: "Cannot Complete/Close Work Order"

**Error: "All operations must be COMPLETED before completing WorkOrder"**
- **Cause**: Not all operations done
- **Solution**:
  1. Review operations tab
  2. Complete remaining IN_PROGRESS operations
  3. Ensure all show status COMPLETED
  4. Then work order can be completed

**Error: "All materials must be ISSUED before completing WorkOrder"**
- **Cause**: Materials not issued from warehouse
- **Solution**:
  1. Contact warehouse team
  2. Ensure all materials marked ISSUED
  3. Verify in Materials tab (status should be ISSUED)
  4. Then work order can be completed

**Error: "Only COMPLETED WorkOrders can be closed"**
- **Cause**: Work order not yet fully completed
- **Solution**:
  1. Complete all operations
  2. Verify all materials issued
  3. Mark work order as COMPLETED
  4. Then CLOSE it

#### Issue 7: "Cannot Cancel Work Order"

**Error: "Cannot cancel WorkOrder while an operation is in progress"**
- **Cause**: Operation currently running (IN_PROGRESS)
- **Solution**:
  1. Complete the in-progress operation first
  2. Once COMPLETED, can then cancel
  3. OR pause operation (if supported)
  4. Contact supervisor if urgent cancellation needed

**Error: "Completed or Closed WorkOrders cannot be cancelled"**
- **Cause**: Work order already finished
- **Solution**:
  1. Cannot reverse completed work
  2. Create adjustment/reversal entry if needed
  3. Contact accounting for corrections
  4. Document reason for adjustment

---

## Best Practices

### Planning Phase

#### 1. Verify BOM Completeness
✓ **Before creating work orders**, verify:
- [ ] BOM has all required components
- [ ] Component quantities are correct
- [ ] BOM is marked "Active" (not obsolete)
- [ ] All substitutes/alternates defined (if applicable)

#### 2. Verify Routing Definition
✓ **Before releasing work orders**, verify:
- [ ] All production steps included
- [ ] Operations in correct sequence
- [ ] Work centers assigned
- [ ] Standard times reasonable (within 20% of historical)

#### 3. Accurate Quantity Planning
✓ **When creating work orders**:
- [ ] Round up to nearest unit (if fractional units problematic)
- [ ] Include allowance for scrap/loss (typically 2-5%)
- [ ] Account for setup/learning curve if needed
- [ ] Coordinate with warehouse for material availability

```
Example: Order for 100 units
  With 3% scrap allowance: Order 103 units (provides buffer)
  Without allowance: Order 100 units (tight planning)
  With 5% scrap allowance: Order 105 units (safe margin)
```

#### 4. Appropriate Date Planning
✓ **Set realistic dates**:
- [ ] Planned Start Date: When materials available + lead time
- [ ] Planned End Date: Start date + estimated duration
- [ ] Due Date: Customer need date (earlier than planned end)
- [ ] Buffer: Add 10-20% time buffer for contingencies

```
Example Timeline:
  Customer Needs: Feb 28
  Plan Due Date: Feb 25 (3-day buffer)
  Plan End Date: Feb 23 (2 days before due)
  Plan Start Date: Feb 18 (5 days before end)
  Material Needed By: Feb 17 (1 day buffer)
```

### Execution Phase

#### 5. Sequence Operations Strictly
✓ **Always execute operations in order**:
- [ ] Never skip operations
- [ ] Don't start next operation before previous complete
- [ ] Let system manage sequence automatically
- [ ] Report if operational dependencies change

**WHY?** Out-of-order execution causes:
- Quality issues (untested assemblies)
- Rework (expensive and time-consuming)
- Inventory confusion (components in wrong locations)
- Audit trail corruption

#### 6. Accurate Completion Reporting
✓ **When completing operations**:
- [ ] Report actual completed quantity (not estimated)
- [ ] Include scrap/loss in total (don't hide defects)
- [ ] Document reason if significant variance
- [ ] Flag quality issues immediately

```
Example - Good Reporting:
  Planned: 100 units
  Completed: 95 units  ← Actual completed
  Scrap: 5 units        ← Documented loss
  Note: "5 units scrapped due to setup misalignment"

Example - Bad Reporting:
  Completed: 100 units  ← Claims all good
  (Actually: 95 completed, 5 defective - hidden)
```

#### 7. Real-Time Material Tracking
✓ **Track materials as operations progress**:
- [ ] Issue materials when operations ready
- [ ] Monitor consumption vs. requirement
- [ ] Report excess consumption immediately
- [ ] Flag shortages early (before they impact schedule)

```
Monitoring Example:
  Material X Requirement: 500 units
  Operation 1 Completed: 100 units
  Expected Material Used: 500 units (all for Op 1)
  Actual Usage: 520 units
  Variance: +20 units (4% scrap)
  Action: Flag for investigation
```

#### 8. Monitor Schedule Performance
✓ **Track planned vs. actual dates**:
- [ ] Compare actual start with planned start
- [ ] Compare actual end with planned end
- [ ] Identify delays early
- [ ] Escalate schedule risks proactively

```
Performance Example:
  Operation 1:
    Planned: Feb 10 08:00 → Feb 10 11:00 (3 hours)
    Actual: Feb 10 08:15 → Feb 10 11:45 (3.5 hours)
    Variance: +30 minutes (risk to end date)
    Action: Add 30 min to next operation start
```

### Completion Phase

#### 9. Complete Operations Promptly
✓ **Don't delay operation completion reporting**:
- [ ] Complete operation same day it finishes
- [ ] Don't batch updates (update daily)
- [ ] Enable other teams to start next operation
- [ ] Improves real-time visibility

**Impact of Delays:**
```
Op 1 Completed: Feb 10 10:00
But reported: Feb 12 09:00 (2-day delay)
Op 2 delayed 2 days → Schedule compressed
Op 3 delayed 2 days → Customer delivery at risk
```

#### 10. Ensure Material Issue Completeness
✓ **Before completing work order**:
- [ ] All materials marked ISSUED in system
- [ ] No leftover/un-issued materials
- [ ] Excess materials returned to stock (if any)
- [ ] Scrap properly documented

#### 11. Archive Complete Work Orders
✓ **When work order done**:
- [ ] Move from "In Progress" to "Completed"
- [ ] Then "Close" for final archival
- [ ] Allows warehouse to free up space
- [ ] Enables final reporting and analysis

### Reporting & Analysis

#### 12. Review Audit Trail Regularly
✓ **For each work order**:
- [ ] Review complete audit history
- [ ] Look for unexpected changes
- [ ] Verify all entries are authorized
- [ ] Document discrepancies

#### 13. Monitor Key Metrics
✓ **Track these performance indicators**:
- [ ] **Completion Rate**: % of planned quantity completed
- [ ] **Schedule Adherence**: Actual vs. planned dates
- [ ] **Material Efficiency**: Actual usage vs. planned
- [ ] **Scrap Rate**: Defective units ÷ total produced
- [ ] **On-Time Delivery**: Work orders closed by due date

```
Monthly KPI Report:
  Average Completion Rate: 98% (target: 95%+) ✓
  Schedule Adherence: 92% on-time (target: 90%+) ✓
  Material Efficiency: 103% (consuming 3% excess) ⚠
  Scrap Rate: 2.5% (target: 2%) ⚠
  On-Time Delivery: 89% (target: 95%) ✗
```

#### 14. Root Cause Analysis
✓ **Investigate significant variances**:
- [ ] Material efficiency >5% variance
- [ ] Schedule delays >10% of plan time
- [ ] Scrap rate >3% of production
- [ ] High revision/rework rates

**Example Investigation:**
```
Issue: Material Efficiency 103% (3% excess)
Investigation:
  • BOM shows 500 units needed
  • Actual used: 515 units
  • Root Cause: 15 units scrapped in Operation 1
  
Actions:
  • Fix setup procedure (caused scrap)
  • Update BOM to reflect historical scrap %
  • Retrain operator on correct procedure
  
Follow-up: Monitor next 10 WOs (same product)
```

#### 15. Continuous Improvement
✓ **Use work order data to improve**:
- [ ] Identify process bottlenecks
- [ ] Optimize standard times
- [ ] Improve material planning
- [ ] Enhance product designs
- [ ] Train operators on weak areas

---

## Summary

The Work Order Management System empowers your production team to:

✓ **Plan** production accurately and completely  
✓ **Execute** operations systematically and safely  
✓ **Track** progress in real-time  
✓ **Manage** materials efficiently  
✓ **Monitor** compliance and quality  
✓ **Report** performance accurately  
✓ **Improve** continuously using data  

### Key Takeaways

1. **Every work order starts in CREATED status** → Edit freely
2. **Releasing locks the work order** → No more edits
3. **Operations execute sequentially** → One at a time, in order
4. **Materials auto-calculate** → Based on BOM × planned quantity
5. **All changes are audited** → Complete traceability
6. **Quantity changes recalculate everything** → Both materials and operations
7. **Completions unlock next steps** → Sequential flow

### Getting Help

- **Questions about creating work orders**: Contact Production Planning
- **Questions about materials**: Contact Warehouse Management
- **Questions about operations/scheduling**: Contact Production Supervisor
- **Technical issues**: Contact IT Support
- **System training**: Contact NextGen Manager Administrator

---

**Document Version**: 1.0  
**Last Updated**: February 2026  
**Next Review**: August 2026  
**Document Owner**: Production Management Team

---

*This document is intended for customers and end-users of the NextGen Manager Work Order Management System. For technical implementation details, please refer to the System Technical Documentation.*
