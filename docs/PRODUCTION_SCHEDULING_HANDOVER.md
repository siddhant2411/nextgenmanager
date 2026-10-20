# Production Scheduling Rebuild — Handover

*Last updated 2026-10-20. Phase 3 under way: step 1 committed (`12520fb`, UI `ed168f7`); step 2 (sub-assembly before parent) built, not committed; step 3 (material) next.*

The scheduler is being rebuilt in six phases. The old one claimed finite capacity but never
subtracted what was already booked, so every work order saw an empty factory.

| Phase | Scope | State |
|---|---|---|
| 1 | Finite capacity, to the minute | Done, committed `65f8fe2` |
| 2 | Dispatch rules, forward/backward, queue/move/overlap | **Done, tested live, not committed** |
| 3 | Material availability, child WO before parent, pin started operations, frozen window | **In progress** — step 1 committed; step 2 built and tested live 2026-10-20, not committed |
| 4 | Planner board: draft/publish plans, drag-drop Gantt, load heatmap, real KPIs | **Part 1 (read-only board) built 2026-10-20, not committed**; part 2 (draft/publish, drag-drop) planned |
| 5 | Optimiser (Timefold) with sequence-dependent setups | Planned |
| 6 | Auto-repair on breakdown, capable-to-promise | Planned |

**Decisions already made by the product owner (2026-10-07):**
- Timefold Solver is approved as a dependency for phase 5. Its version has not yet been checked against Spring Boot 3.3.5.
- Labour and operators are **not** a scheduling constraint for now. There is no per-worker time capture to drive it.
- `/schedule`, `/reschedule` and `/schedule-all` must keep working, with their old behaviour as the default.

---

## 1. How the scheduler works now

The code is in `production/service/scheduling/`.

- **`ProductionSchedulerService`** is the only entry point. It loads data, plans, and writes the dates back to the operations and the work order.
- **`engine/`** holds pure Java with no Spring or JPA, unit-tested without a database:
  - `WorkCalendar` — when a work centre is open. It walks forward (`advance`), walks back (`retreat`), and counts productive minutes between two moments.
  - `WorkCalendarFactory` — builds a calendar from shifts and the holiday calendar. **If a work centre has no shift rows, it gets one shift a day of `availableHoursPerDay`, starting at 09:00, or at 00:00 if that is more than 15 h.**
  - `ResourceTimeline` — what is booked on one machine (or on a work centre with no machines). `findSlot` gives the earliest slot that fits; `findLatestSlot` gives the latest.
  - `DispatchSequencer`, `DispatchRule`, `ScheduleDirection`.

**The capacity ledger is not a table.** Each run rebuilds it from the live `WorkOrderOperation` rows (resource plus planned start and end) and from machine downtime. A booking therefore disappears as soon as its operation is completed, cancelled or deleted.

**Each work order is booked all or nothing.** Planning happens on a private copy of the timelines, which is merged into the ledger only once the whole order fits. A failure partway through a batch leaves nothing half-booked.

**Rules that hold across all phases:**
- A job may pause while its work centre is closed, but it keeps its machine until it ends. A booking or downtime in the middle forces it to start again later.
- Shift breaks are spread across the shift as a rate (450/480), because shifts store how long breaks are but not when they fall.
- Operation duration = setup + run per unit × **the operation's own** planned quantity.

## 2. What phase 2 added

### Dispatch rules (`/schedule-all?rule=`)
- **PRIORITY** (the default, unchanged): priority first, then due date.
- **EDD**: earliest due date first.
- **CRITICAL_RATIO**: time left until due ÷ work left, lowest first. Overdue orders come first, most overdue at the top.
- **ATC**: apparent tardiness cost, with weight 4 (URGENT) down to 1 (LOW) and k = 2.

Rules order **whole work orders**, not individual operations, so each order is still booked all or nothing. "Time left until due" is counted in productive minutes on the calendar of the order's first work centre, so it compares with work minutes in the same unit.

### Backward scheduling (`?direction=BACKWARD`)
- The last operations end by the due date. A due date entered as a plain date means the end of that day.
- Each predecessor then takes the latest slot that still hands over in time. Among machines, the one that can start latest wins.
- The earliest allowed start is **now**.
- With no due date, or when the order can't fit between now and the due date, it is scheduled forward and a warning says why.
- `/reschedule?startDate=` is always forward.

### Time between operations (V188, on `routingOperation`)
- **`queueTime`** — the minimum wait after material arrives, applied **only between operations**.
- **`moveTime`** — transit after an operation.
- **`transferQuantity`** — output is passed on in batches of this size. Null, or not below the operation's quantity, means the whole lot moves at once.
- Queue and move are **wall-clock** minutes.
- With a transfer batch, the successor's start is at least: the predecessor's first batch done (setup + run × batch) + move + queue.
- Its end is at least: the predecessor's end + move + run × batch.
- Finite capacity cannot hit both bounds in one search, so the slot is nudged (up to 50 rounds); backward mirrors this.
- Fields are read from the routing operation, not copied onto the work order operation.
- Every save path (routing endpoint, BOM update, new BOM revision) goes through `RoutingServiceImpl`, which maps the fields.

### Bug fix: the planned start date was treated as a floor forever
Forward scheduling used the work order's saved `plannedStartDate` as a "don't start before" date. The scheduler overwrites that field every time it plans, so a scheduled order could never be pulled earlier: after Schedule Just in Time, clicking Auto Schedule changed nothing.

The saved date now counts **only while `scheduledAt` is null**, that is, before the first schedule. After that, Auto Schedule means "from now", and Reschedule with a date still sets an explicit start.

### UI (in the `ui` repo)
- **Schedule All** opens an options dialog (rule and direction, each explained). Its last choice is remembered in view state.
- Also fixed in the results dialog: it read `scheduledCount` / `warnings` / `failures`, which the endpoint never sent, so it always said "0 scheduled". The endpoint returns a list of per-order results.
- The work order **Actions** menu has **Schedule Just in Time**.
- The routing editor has a **Between Operations** section: Queue, Move, Transfer batch.

### Files

These are all uncommitted.

**Backend:**
- `ProductionSchedulerService`
- `engine/{ResourceTimeline, WorkCalendar}`
- new: `engine/{DispatchRule, DispatchSequencer, ScheduleDirection}`
- `WorkOrderController`, `ScheduleResultDTO` (now carries `direction`)
- `RoutingOperation`, `RoutingOperationDto`, `RoutingServiceImpl`
- `V188__routing_queue_move_overlap.sql`
- tests: `ProductionSchedulerServiceTest`, `ResourceTimelineTest`, `WorkCalendarTest`, `DispatchSequencerTest`

**UI:** `WorkOrderList.jsx`, `AddUpdateWorkOrder.jsx`, `BomRouting.jsx`, `AddBom.jsx`, `services/workOrderService.js`

**Regression:** `nextgen-regression/playwright/scheduling-phase2-{api,ui}.js`. That folder is not under git.

## 3. How to test

**Unit tests:**
- `./mvnw test -Dtest='ProductionSchedulerServiceTest,ResourceTimelineTest,WorkCalendarTest,DispatchSequencerTest'` — 56 tests.
- The full suite has a known red baseline: **2 failures in `MachineEventServiceImplTest` and 1 error in `NextgenmanagerApplicationTests`**. Compare the failing classes, not the totals.

**Live, against `ngm_test_server`** (V188 is applied there; it was last run 2026-10-19 with all checks passing):
```
cd nextgen-regression
node playwright/scheduling-phase2-api.js   # 26 checks
node playwright/scheduling-phase2-ui.js    # 8 browser steps, screenshots in playwright-output/scheduling-phase2/
```
- Both scripts create their own work orders on BOM 7 / routing 7 (Spacer Bush: CNC Turning on WC-CNC/CNC-01, then Final Inspection on WC-QC). They cancel those orders and clear routing 7's timing fields at the end.
- The UI script refuses to run if any CREATED work orders already exist, because Schedule All would schedule them too.
- WC-CNC and WC-QC have **no shift rows**, so they run 00:00–16:00 and 09:00–17:00, every day, holidays included.

**Gotchas:**
- **Do not start the backend yourself.** The root `application-local.properties` points at a different database with `ddl-auto=update`. Ask for it to be started.
- The `local` profile has Flyway off, so new migrations must be applied to `ngm_test_server` by hand **before** the backend restarts. Otherwise Hibernate selects columns that don't exist yet.
- From Git Bash, set `MSYS_NO_PATHCONV=1`. Otherwise a `/production/...` argument is rewritten into a Windows path.
- The scheduler fills gaps. A short order sequenced second can still start before a long one sequenced first, so to check dispatch order use the order of the `/schedule-all` response list, not machine start times.
- Overlap only shows when the successor's work centre is open while the predecessor runs. QC opens at 09:00, so turning has to run inside QC hours for the test to see it.
- VS Code's Java tooling compiles classes behind Maven's back. If `mvnw` says "Nothing to compile" while the test count looks stale, check `target/surefire-reports`.

## 4. Known limitations carried forward

- ~~Every non-cancelled operation is placed again on each run, including IN_PROGRESS and COMPLETED ones.~~ Fixed in phase 3 step 1.
- ~~Only CREATED and SCHEDULED work orders can be scheduled.~~ Widened in phase 3 step 1. `/schedule-all` still takes CREATED orders only.
- Work orders scheduled by the old engine carry midnight dates and book little or no capacity until rescheduled.
- Backward mode ignores the work order's requested start. Its floor is always now.
- Dispatch is per work order, not per operation.
- Queue time is not applied before the first operation.
- No validation rejects a negative queue, move or transfer value. The scheduler treats them as zero.
- Calendars with overlapping shifts on the same day are not merged and could double-count capacity. None exist today.

## 5. Phase 3

### Product-owner answers (2026-10-19)
- **HOLD operation:** keeps its dates and machine and stays booked; successors wait for it.
- **Replannable work orders:** CREATED, SCHEDULED, IN_PROGRESS, MATERIAL_PENDING, READY_FOR_PRODUCTION, PARTIALLY_READY, MATERIAL_REORDER, HOLD.
- **Missing material delays the schedule**, with a warning naming the binding item and PO.
- **Supply:** free stock (on hand minus reserved, consumed in plan order), open POs, approved purchase requisitions (dated by lead time), then item lead time as the fallback.

### Step 1 built: pin started operations (2026-10-19, uncommitted, no migration)
- `pin()` in `ProductionSchedulerService` runs before each planning attempt (forward, and each backward attempt) and returns the operations held in place. `planForward` / `planBackward` take them as given.
- **COMPLETED:** not saved, books nothing. Successors hand over from `actualEndDate` (then planned end, then now).
- **IN_PROGRESS:** keeps its assigned machine (or takes the first working one, with a warning) and its `actualStartDate`. End = run × (planned − completed − scrapped − rejected), worked from now on its calendar; setup is assumed done. It is booked **even over other work**, since it is physically on the machine, and the clash is warned. Only its end date (and start, if it had none) is saved.
- **HOLD:** keeps and books its planned slot, not saved, warned. A HOLD operation with no planned dates is planned like the rest.
- **Other orders' running work:** `findBookedLoad` now also returns IN_PROGRESS operations whatever their dates, and the ledger books them to at least their projected end. Before, an operation overrunning its planned end dropped out of the ledger and its machine looked free.
- Only a CREATED order becomes SCHEDULED. Every other replannable status is kept; only the dates change.
- `ScheduleResultDTO.OperationSchedule` gained `status` and `pinned`.
- UI: `AddUpdateWorkOrder.jsx` `canScheduleWorkOrder` now uses the same status list. The compact schedule card shows for any replannable order the scheduler has planned (`autoScheduled`), labelled PLANNED unless the status really is SCHEDULED; before, it said SCHEDULED regardless and vanished on reload for a started order. `ScheduleDialog.jsx` marks held operations: a Done / Running / On hold tag, hatched Gantt bar, a "Plan" column in the table, "N planned · N kept" under Operations, and an info note.
- Browser test 2026-10-20: `scheduling-phase3-pin-ui.js` 8/8 (material state → start → reload → Auto Schedule → table → Reschedule → cancelled order switched off), screenshots in `playwright-output/scheduling-phase3-pin/`; `scheduling-phase2-ui.js` still 8/8.
- Tests: 9 new in `ProductionSchedulerServiceTest` (65 in the four scheduling classes, all green).
- **Tested live 2026-10-20** on `ngm_test_server` after a restart (no migration): `nextgen-regression/playwright/scheduling-phase3-pin-api.js` 11/11, and `scheduling-phase2-api.js` still 26/26, so the changed `findBookedLoad` JPQL runs. The script takes an order through release (MATERIAL_PENDING), store approval and the start of its first operation, then checks the running operation stays pinned forward, on a repeat run and backward, and that a second order queues behind it on CNC-01. It **stops before completing anything** (completion consumes stock and posts WIP vouchers that cancelling would not undo), so COMPLETED and HOLD are covered by unit tests only. Cancel returns the reserved stock; the material request record stays APPROVED, which is existing behaviour.
- **Caveat:** if operators record quantities only at the end, `completedQuantity` stays 0 while running, so the projection assumes the whole quantity is still to make. That is conservative.

### Step 2 built: sub-assembly before parent (2026-10-20, uncommitted, no migration)
- **Which children count:** `parentWorkOrder` points at the order, it is not a split of it (`splitFromWorkOrderId`), it is not cancelled, and its output item (per active line, else its BOM's item) is one of the parent's `WorkOrderMaterial` components. Rework orders also set `parentWorkOrder` (`RejectionServiceImpl`), but they make the parent's own item, so the material match drops them along with splits.
- **The constraint ("floor"):** the parent operation the material is issued to (`WorkOrderMaterial.workOrderOperation`), or else the first operations of the order (of the material's line, if it has one), cannot start before the child is ready = the child's last operation end + that operation's move time. A completed operation uses its actual end. A finished child (COMPLETED, CLOSED, SHORT_CLOSED) imposes nothing.
- Floors are generic (`Floor(at, reason)` per operation, built in `floors()`), so material availability (step 3) adds to the same map.
- **Forward:** the floor is one more "not before" on the operation; when it binds, a warning names it ("waits for sub-assembly WO-…, ready 24 Oct 2026 14:00").
- **Backward:** the floor raises that operation's earliest allowed start above now. If it cannot fit, the order falls back to forward and the warning says "would have to start before sub-assembly WO-… is ready".
- **Single order:** a child that was never planned gives a warning ("not scheduled yet … schedule it first") and is not waited for. The child is not scheduled automatically: scheduling one order never re-plans another.
- **Child re-planned later than its parent needs it:** warns, naming the parent ("WO-… needs this order's output at …, but it will be ready only at …"). The parent is not moved.
- **Schedule All:** orders are grouped by depth among the batch's sub-assemblies. Forward runs leaves first (children before parents); backward runs parents first, and each child is then planned back from when its parent needs it (its consuming operation's start, minus the child's final move), or its own due date if sooner. The dispatch rule orders work within each level. A loop in the parent links counts as depth 0.
- New: `ProductionSchedulerService` reads `WorkOrderMaterialRepository.findByWorkOrderId` and `WorkOrderRepository.findByParentWorkOrderIdAndDeletedDateIsNullOrderByIdAsc`. The ledger carries `readyAt` (orders planned this run) and `batch`.
- Tests: 10 new in `ProductionSchedulerServiceTest` (75 in the four scheduling classes, all green). With the floors switched off, the 5 tests that depend on them fail.
- **Live:** `nextgen-regression/playwright/scheduling-phase3-subassembly-api.js`, on BOM 3 (Wheel Hub Assembly, FG-0002) with a child on BOM 2 (Wheel Hub Machined, SF-0001); the parent's SF-0001 material is tied to its Hub Assembly operation. Run against the **old** backend it showed the bug: Hub Assembly planned for 20 Oct 13:50 while the hub it uses was machined only by 21 Oct 00:38. After the restart (2026-10-20) it passed **13/13**: unscheduled-child warning; Hub Assembly moved to 21 Oct 09:00, after the hub was ready at 00:47; pushing the child later warns and naming the parent; Schedule All forward planned the LOW child before its URGENT parent; Schedule All backward planned the parent first and the child ready at 15:18, the minute Hub Assembly needs it. `scheduling-phase2-api.js` 26/26 and `scheduling-phase3-pin-api.js` 11/11 still pass. Do not pipe it through `head`: that kills it before its cleanup.

### What was found in the code

Phase 3 is "the plan respects reality": material, multi-level work orders, work already under way, and a near-term window nobody should churn.

### 5.1 Material availability
**Goal:** an operation, or the whole order, does not start before its material can be there.

What exists:
- `WorkOrderMaterial` holds `component`, `netRequiredQuantity`, `issuedQuantity`, `issueStatus`, an optional `workOrderOperation` (the material is needed at that operation) and `inventoryRequestId`.
- Stock: `ProductInventorySettings.availableQuantity`. Per-warehouse rows exist since Phase F, and the order has `produceIntoWarehouse` / material warehouse codes.
- Incoming supply: `PurchaseOrder.expectedDeliveryDate`, at the header (check whether `PurchaseOrderItem` has one per line). There are also open purchase requisitions.
- Lead times: `InventoryItemDTO.leadTime` and `ItemVendorPrice.leadTimeDays`. `MakeBuyAnalysisServiceImpl.getLeadTimeDays` already resolves one, so reuse it.

Suggested approach:
- For each material, the release time is now if free stock covers it; otherwise the earliest incoming PO date that covers it; otherwise now + lead time.
- Apply it as a floor on the operation the material is tied to, or on the first operation when it isn't tied to one.
- Free stock must be **consumed in plan order across the batch**, otherwise two orders both "see" the same stock. That means a material ledger next to the capacity ledger, built the same way (derived, not a table).
- Report the binding material as a warning, e.g. "starts 24 Oct because of 40 × RM-0012 on PO/0031".

Open questions for the product owner:
- Should missing material **delay** the schedule, or schedule anyway and only warn?
- Use reserved stock only, or all free stock?
- Count purchase requisitions as supply, or POs only?

### 5.2 Child work order before parent
- `WorkOrder.parentWorkOrder` is written for **two different things**: a sub-assembly order raised for a parent, and a quantity **split** off an order. `splitFromWorkOrderId == parent.id` tells them apart (see `WorkOrderServiceImpl.getRelatedWorkOrders`, about line 860). **Only real sub-assembly children should constrain the parent.** Do not chain splits.
- The parent's operation that consumes the child's item (match on `WorkOrderMaterial.component` against the child's BOM output) cannot start before the child's last operation ends, plus move time.
- In a batch, schedule children before their parents: sort by depth, then apply the dispatch rule within each level.
- In backward mode the parent is placed first, and its consuming operation's start becomes the child's due date.

### 5.3 Pin operations that have started
- `OperationStatus` is `PLANNED, WAITING_FOR_DEPENDENCY, READY, IN_PROGRESS, COMPLETED, HOLD, CANCELLED`, and the operation has `actualStartDate` / `actualEndDate`.
- **COMPLETED:** do not move it. Its booking is already out of the ledger (`findBookedLoad` drops finished operations); successors use `actualEndDate` as the predecessor's end.
- **IN_PROGRESS:** keep its machine and `actualStartDate`. Its end = the remaining quantity (planned − completed − scrapped) × run, from now. Book that, then plan the rest around it.
- **HOLD:** decide with the product owner whether it is skipped or placed after the hold is released.
- Allow scheduling work orders in **IN_PROGRESS** status (today it throws 409) so that a started order's remaining operations can be replanned.
- `WorkOrderStatus` is a long list: CREATED, SCHEDULED, MATERIAL_PENDING, READY_FOR_PRODUCTION, PARTIALLY_READY, RELEASED (deprecated), IN_PROGRESS, MATERIAL_REORDER, READY_FOR_INSPECTION, TESTING, INSPECTION_FAILED, COMPLETED, CLOSED, CANCELLED, SHORT_CLOSED, HOLD. Decide which of these count as replannable.

### 5.4 Frozen window
- Nothing exists for this yet. It needs a setting, probably per company and in hours: operations starting inside `now + frozen window` keep their dates on a re-run, and everything else plans around them.
- It is a migration plus a settings field. Check whether a company or production settings entity already exists before adding one.
- Batch scheduling only takes CREATED orders today, so the window mostly matters once phase 4 introduces a "re-plan everything" action. Phase 3 can build the rule and its tests ahead of that.

### 5.5 Suggested order of work
1. ~~**Pin started operations.**~~ Done (step 1 above). It removes the worst current behaviour (replanning finished work) and gives later steps a correct base.
2. ~~**Child before parent.**~~ Built (step 2 above).
3. **Material availability.** Biggest, and it needs the product owner's answers in 5.1 first.
4. **Frozen window.**

Extend `scheduling-phase2-api.js` into a phase 3 script in the same style: create its own data, check invariants rather than clock times, and clean up after itself.

## 5b. Phase 4 part 1 — the read-only planner board (2026-10-20, uncommitted, no migration)

Pulled ahead of phase 3 step 3 because the old Schedule Views page reported wrong numbers.

**What was wrong with the old page (`ProductionScheduleView.jsx`, now deleted):**
- "Utilization" was completed ÷ planned minutes: progress, not how busy anything is. A fully booked week showed 0%.
- One column per day over a minute-precise plan; no hour axis.
- Sunday hard-coded as a holiday, ignoring the work-centre calendars.
- Only machines with work were listed, so free capacity was invisible.
- Operations selected by `plannedStartDate >= from`, so a job that began before the window vanished from it.
- Bars did not open their work order; no now line, no late orders, no warnings.

**Backend:** `GET /production/work-order/schedule/board?from=&to=` (`to` inclusive, max 92 days; defaults to today + 6) → `ScheduleBoardDTO`, built by `ScheduleBoardService`.
- Working time comes from `WorkCalendarFactory`, the same calendars the scheduler plans with. Rows: every live machine, or the work centre when it has none, plus an "unassigned" row for operations on a work centre with machines but none assigned (it adds no capacity).
- Load = booked productive minutes (each operation clipped to the window) ÷ available productive minutes.
- Operations by overlap: new `WorkOrderOperationRepository.findBoardOperations`.
- Alerts: late (planned end after due) and at risk (under a day to spare), counting only orders the scheduler has planned (`scheduledAt` set); unscheduled CREATED orders; IN_PROGRESS operations past their planned end (via `findBookedLoad`); machines not ACTIVE or in downtime now. New `WorkOrderRepository.findOpenWithDueDate`.
- The old `/schedule/combined`, `/work-center/{id}` and `/machine/{id}` endpoints are untouched.
- Tests: `ScheduleBoardServiceTest`, 8.

**UI:** `components/production/schedule/{ScheduleBoard, BoardTimeline}.jsx`; `pages/ProductionSchedulePage.jsx` now renders `ScheduleBoard`.
- Hub layout: dark hero, four KPI cards (load, late, at risk, idle machines), clickable alert panels (late, running over, at risk, not scheduled, machines down).
- Gantt: Day / 3 days / Week / 2 weeks; By machine (grouped by work centre, each row with a load meter) or By order (sub-assemblies under the order that uses them, due-date marker). Closed time hatched grey, downtime red, now line, opens scrolled to now.
- Bars: colour by status, priority edge, red ring when the order will be late, hatched when kept, dashed red tail when running over. Click opens the work order. Overlapping bars split into lanes and the row is flagged **Clash**.
- Search dims what does not match. Zoom and view are remembered per browser (localStorage).
- Browser test: `nextgen-regression/playwright/schedule-board-ui.js` 8/8 (seeds a sub-assembly pair, a too-big order, a running operation and an unscheduled order; `--keep` leaves them for a look). Screenshots: `playwright-output/schedule-board/` and `schedule-views-review/` (`shot-schedule-views.js`).

**Bug found by the board, fixed in the scheduler:** a running operation kept its old planned start when rescheduled, so a job started earlier than planned was saved starting *after* its new end (WO/2026-27/0114: start Thu 05:25, end Wed 00:54). Now its planned start becomes its actual start. Unit test added; needs a backend restart to take effect. Rows saved before the fix are still drawn across their span and laned correctly.

**Part 2 (planned):** draft / publish plans, drag a bar to another slot or machine with the capacity check behind it, load heatmap.

## 6. After phase 3

- **Phase 4, planner board.** New module pages must copy the Sales Order hub's look (dark hero, frosted stat cards, alert rail, registry card), with detail on its own route. `/production/schedule/*` already exists in the UI routes and `ProductionScheduleViewService` serves the combined and per-machine views.
- **Phase 5.** Check the Timefold version against Spring Boot 3.3.5 first.
- **Phase 6.** Downtime events already block machine timelines. Auto-repair would trigger a replan of the affected operations when a breakdown is logged.
