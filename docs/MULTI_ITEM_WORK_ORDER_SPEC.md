# Multi-Item Work Orders & Work Order Splitting — Implementation Spec

*Planned 2026-08-09. Prereq: none beyond current `master`+`feature`. Latest migration on disk: **V149**, so this spec starts at **V150**.*

---

## Build status (updated 2026-08-09)

| Phase | State | Notes |
|---|---|---|
| **A** — line table + shim | **DONE** | `WorkOrderLine`, `WorkOrderLineRepository`, **V150** (verified against real Postgres incl. backfill) |
| **B** — consumers line-aware | **DONE** | per-line cost pools; no work-order `getBom()` reads remain |
| **C** — multi-line creation + UI | **DONE (except V152)** | `lines[]` on request/response, SO batching (flagged **off**), Items table + Additional Items editor. **V151** fixes two per-line scoping defects found in live testing (see trap 11). |
| **D** — split by quantity | not started | |
| **E** — split by line | not started | |
| **F** — split by operation | not started | mostly gap-closing; job-work already exists |

**Deliberately deferred out of C:**
- **V152** (dropping `workOrder.bomId` / `routeId` / `plannedQuantity`). The header mirror is still read by the DTO layer, the MapStruct mapper and the UI. Dropping it now breaks the screen; it goes with the work that moves those reads onto lines.
- Header `plannedQuantity` is currently the **sum across lines** — the least-wrong scalar, but it still adds unlike units of measure. See open decision 1.

**Feature flag:** `nextgen.production.batch-work-orders-per-sales-order` (default `false`) turns on one-WO-per-SO batching.

**Test baseline:** 154 tests, 8 failing — all pre-existing and outside the work-order area (`InventoryItemServiceImplTest` ×4, `MachineEventServiceImplTest` ×2, `contextLoads`, `WorkOrderSummaryTest`). `WorkOrderServiceImplTest` is green: 21 run, 2 quarantined pending open decision 6.

> **Goal:** Let one work order carry **several finished items as lines** (header + lines, like a sales order), and let a planner **split** a work order three ways: by quantity, by line, and by operation (to job-work). Today a WO is hard-wired to exactly one item, and an SO with five manufactured lines produces five unrelated WOs.

---

## Guiding principle

**The line becomes the unit of manufacture; the header becomes a shared envelope.**

Everything that is *physically about making one item* — BOM, routing, quantity, materials, operations, tests, yield, cost — moves to `WorkOrderLine`. Everything that is *administrative* — number, customer/SO, priority, dates, release, remarks — stays on `WorkOrder`.

The single most important consequence: **cost pools must partition by line.** Today `realUnitCost = totalCost / totalCompleted` ([WorkOrderServiceImpl.java:2062-2065](../src/main/java/com/nextgenmanager/nextgenmanager/production/service/workorder/WorkOrderServiceImpl.java#L2062-L2065)). Applied unchanged to a WO holding 10 pumps and 40 flanges, it would value every flange at the blended average of a pump — smearing five-figure cost across trivial parts and corrupting FG stock valuation and COGS. Per-line cost partitioning is not a refinement; it is the correctness bar for this feature.

---

## Exit criteria (done when all true)

1. A WO can be created with N lines, each with its own item, BOM, routing and quantity; N=1 behaves exactly as today.
2. Materials, operations, tests, rejections and labour are scoped to a **line**, not the WO.
3. Completion posts **one PRODUCE per line**, each at that line's own unit cost. No cross-line cost bleed.
4. WIP nets to ~zero per WO *and* per line on a fully completed WO; the Phase-3 GL↔stock reconciliation still ties.
5. An SO with several manufactured shortfalls yields **one WO with several lines**, not several WOs.
6. **Split by quantity**: WO of 100 → 60 + 40, with materials/operations re-proportioned, issued stock left on the original, and operation dependencies remapped to the new WO's own operations.
7. **Split by line**: one or more lines detach into a standalone WO carrying their own history and cost.
8. **Split by operation**: an operation the routing marked in-house can be farmed out mid-flight, producing a `JobWorkChallan` and blocking downstream operations until it returns.
9. Every split is auditable: new WOs record what they were split from, and the event appears in WO history.
10. No regression in scheduling capacity: a split does not double-book work-center minutes.

**Out of scope:** co-products/by-products from a single BOM (a different feature — one BOM, many outputs); automatic multi-level BOM explosion into child WOs (`parentWorkOrder` stays as-is); merging WOs; cross-WO operation dependencies.

---

## Conventions locked from the codebase (follow exactly)

- `model → repository → service (interface+impl) → controller → dto → mapper`.
- Column name = exact field name (`PhysicalNamingStrategyStandardImpl` — no snake_case).
- Soft delete via `deletedDate`; `WorkOrder` carries `@Where(clause = "deletedDate IS NULL")` ([WorkOrder.java:29](../src/main/java/com/nextgenmanager/nextgenmanager/production/model/WorkOrder.java#L29)) — `WorkOrderLine` must too, and every "how many lines" check must count only non-deleted rows.
- Enums `@Enumerated(EnumType.STRING)`; money/qty `BigDecimal`.
- Never edit an existing Flyway migration; new files only, from **V150**.
- WO numbering goes through `WorkOrderNumberGenerator` (`WO/{FY}/{####}`, NumberSequence-backed). **Split-created WOs must use it** — never hand-build a number.
- Audit via `WorkOrderAuditService` + `WorkOrderEventType`.

---

## What already exists (reuse — do NOT rebuild)

| Need | Source |
|---|---|
| FY-scoped WO numbering | `WorkOrderNumberGenerator.next()` / `.preview()` |
| Operation dependency graph | `WorkOrderOperation.dependsOnOperationIds`, `parallelPath`, `dependencyResolvedDate` |
| Dual-gate qty control | `WorkOrderOperation.availableInputQuantity` + `completedQuantity` |
| Material issue gating | `WorkOrderMaterial.workOrderOperation` (op must be active before issue) |
| Job-work / subcontract | **Fully built**: `JobWorkChallan` + `JobWorkChallanLine`, `ChallanStatus` lifecycle, `create/dispatch/receiveBack/cancel`, `syncLinkedOperation` (syncs op status + qty on return), `consumeMaterials`, GST Sec-143 tracking. Already FK'd to `workOrderOperation`. |
| Subcontract cost type | `CostType.SUB_CONTRACTED` on `RoutingOperation` |
| GL posting | `InventoryPostingListener` — CONSUME/PRODUCE/RETURN on `referenceType=WORK_ORDER`, idempotent per `InventoryLedger.id` |
| Scheduling | `ProductionSchedulerService` (capacity-aware forward scheduling), `ProductionScheduleViewService` |
| Per-op costing | `CostOfProductionServiceImpl` (already computes per-operation est/actual with correct ÷60) |

**Because job-work is already end-to-end, "split by operation" is a gap-closing phase, not a build-from-scratch phase.** See Phase F.

---

## ⚠ Traps found in the code (each must be handled explicitly)

1. **Blended unit cost** — `realUnitCost = totalCost / totalCompleted` smears cost across unlike items. Must partition per line. *(the headline trap)*
2. **`getBom()` is the item lookup** — 23 call sites read the produced item as `workOrder.getBom().getParentInventoryItem()`. Every one is a single-item assumption. A `WorkOrderLine.inventoryItem` field should be **explicit**, not re-derived from the BOM, so a line's identity survives BOM revision.
3. **`parentWorkOrder` is already taken** — it means *parent assembly* (`WorkOrderSourceType.PARENT_WORK_ORDER`). Do **not** overload it for splits. Add a separate `splitFromWorkOrderId` + `splitFromLineId`.
4. **Operation dependencies are raw IDs** — `dependsOnOperationIds` is a `Set<Long>` of `WorkOrderOperation` ids ([WorkOrderOperation.java:115-121](../src/main/java/com/nextgenmanager/nextgenmanager/production/model/WorkOrderOperation.java#L115-L121)). A naive split copies those ids verbatim, leaving the *new* WO's operations depending on the *old* WO's operations — a silent cross-WO dependency that will deadlock or falsely release. Splits must remap old-id → new-id.
5. **Issued material cannot be re-proportioned** — `issuedQuantity`/`consumedQuantity` represent stock that has physically left the store. Rule: **issued and consumed quantities stay with the original WO; only the un-issued balance is transferable.** A split that would leave the original with more issued than planned must be rejected.
6. **Scheduling double-book** — a split creates a second WO; unless the original's reserved capacity is released/recomputed first, work-center minutes are counted twice.
7. **Yield maths is per-item** — `getFirstPassYield()` / `getScrapRate()` / `getOverallYield()` divide by `plannedQuantity` ([WorkOrder.java:153-184](../src/main/java/com/nextgenmanager/nextgenmanager/production/model/WorkOrder.java#L153-L184)). Across 10 pumps + 40 flanges these are meaningless. Move to line; header-level yield must be either removed or explicitly defined (recommend: weighted by line, and clearly labelled).
8. **Auto-reserve targets the SO header** — reserve uses `salesOrder.getOrderNumber()` ([WorkOrderServiceImpl.java:2085-2097](../src/main/java/com/nextgenmanager/nextgenmanager/production/service/workorder/WorkOrderServiceImpl.java#L2085-L2097)). With one WO covering several SO lines, reservation must resolve per line.
9. **Header aggregates** — `estimatedProductionMinutes`, `estimatedTotalCost`, `completedQuantity`, `scrappedQuantity` become **sums over lines**. `completedQuantity` as a bare number across mixed UoMs is nonsense; keep it only as a per-line figure and drop or clearly qualify the header roll-up.
10. **Status has two levels now** — a WO with one line COMPLETED and one IN_PROGRESS needs a defined header status. Recommend: header status is *derived* as the least-advanced active line, with `PARTIALLY_COMPLETED` added.

---

11. **Operation sequence is per line, not per work order** *(found in live testing, fixed in V151)*.
    Two things assumed a work order ran exactly one routing, so sequence numbers could not repeat:
    - `UNIQUE (workOrderId, sequence)` from V34 (`uq_woo_work_order_sequence`). Two lines each with
      an operation 10 is normal, and the second line's insert was rejected outright. Replaced with
      `UNIQUE (workOrderLineId, sequence)`, which keeps the original guarantee within a routing run.
    - The prev/next-operation lookups (`findTopByWorkOrderAndSequence…`) were scoped to the work
      order. Silently worse than the constraint: completing a pump's operation 10 would have
      forwarded its good quantity into a *flange* operation. Line-scoped variants added; the
      work-order-scoped ones are `@Deprecated` so nothing new reaches for them.

## Data model

### New: `WorkOrderLine`

| Field | Notes |
|---|---|
| `id` | |
| `workOrder` | FK, `nullable=false` |
| `lineNumber` | ordering within the WO |
| `inventoryItem` | **explicit FK** — the finished item (trap 2) |
| `bom`, `routing` | moved off the header |
| `plannedQuantity`, `completedQuantity`, `scrappedQuantity` | moved off the header |
| `status` | line-level `WorkOrderStatus` |
| `salesOrderItemId` | optional — which SO line this satisfies (trap 8) |
| `dueDate` | optional per-line override of the header date |
| `estimatedProductionMinutes`, `estimatedTotalCost` | per line |
| `splitFromLineId` | provenance (trap 3) |
| `creationDate` / `updatedDate` / `deletedDate` | standard |

Yield accessors (`getFirstPassYield` etc.) move here verbatim from `WorkOrder`.

### Re-parented children

`WorkOrderMaterial`, `WorkOrderOperation`, `WorkOrderTestResult` gain `workOrderLineId`. `RejectionEntry`, `ScheduleDecisionLog` and `JobWorkChallan` already carry `workOrderId`; they gain `workOrderLineId` alongside it (keep both — the WO-level index stays useful for lookups).

`WorkOrderLabourEntry` and `WorkOrderQaEntry` hang off `WorkOrderOperation` already and need no change.

### `WorkOrder` changes

Gains `splitFromWorkOrderId`, `lines`, and `isMultiLine`. Retains `bom`/`routing`/`plannedQuantity` **columns** through Phases A–B as the compatibility shim, then drops them in Phase C.

---

## Migration plan

- **V150** — create `workOrderLine`; add `workOrderLineId` to the five child tables; backfill **exactly one line per existing WO** from its current header values; backfill child `workOrderLineId`. Old header columns left in place and still populated.
- **V151** (end of Phase B) — `NOT NULL` on every `workOrderLineId`; indexes.
- **V152** (Phase C) — drop `workOrder.bomId`, `routeId`, `plannedQuantity` once no code reads them.
- **V153** (Phase D) — `splitFromWorkOrderId`, `splitFromLineId`, `PARTIALLY_COMPLETED` status value.

Backfill is pure INSERT-SELECT and is fully reversible until V152.

---

## Work-streams (build order — each independently shippable)

### Phase A — Line table + compatibility shim *(no behaviour change)*

Create the entity, repository, V150. Every existing WO gets exactly one line.

Add **deprecated delegating accessors** on `WorkOrder`:

```java
@Deprecated // reads the sole line; throws once WOs can be multi-line
public Bom getBom() {
    return soleLine().getBom();
}
private WorkOrderLine soleLine() {
    List<WorkOrderLine> active = activeLines();
    if (active.size() != 1) {
        throw new IllegalStateException(
            "WorkOrder " + workOrderNumber + " has " + active.size()
            + " lines; caller must be line-aware.");
    }
    return active.get(0);
}
```

This is the whole safety strategy: the shim keeps all 23 call sites compiling and behaving identically for single-line WOs, and **fails loudly** the moment a multi-line WO reaches un-migrated code. Multi-line creation stays blocked until Phase C, so the throw is unreachable in production during A–B.

*Exit:* full app compiles, existing WO tests unchanged, nothing behaves differently.

### Phase B — Make consumers line-aware *(depends on A)*

Migrate off the shim, in this order (cheapest and most isolated first):

1. `WorkOrderExportServiceImpl` (job sheet, pick list, move tickets) — read-only, no risk
2. `ProductionScheduleViewService`, `MachineScheduleService` — read-only views
3. `TestTemplateServiceImpl`, `RejectionServiceImpl`, `WorkOrderQaService`
4. `CostOfProductionServiceImpl` — **per-line cost pools** (trap 1)
5. `ProductionSchedulerService` — schedule per line, aggregate to header
6. `WorkOrderServiceImpl` — the big one: create, release, issue, complete, cancel, short-close

Within `WorkOrderServiceImpl.completeWorkOrder`, replace the single blended cost block with a per-line loop: partition `WorkOrderMaterial` and `WorkOrderOperation` cost by their line, compute that line's `realUnitCost`, emit one `produceStock` per line with that line's `inventoryItem`, and reserve against that line's SO line.

*Exit:* zero references to the deprecated shim; delete it.

### Phase C — Multi-line creation + UI *(depends on B)*

- `WorkOrderRequestDTO` gains `List<WorkOrderLineRequestDTO> lines`; the flat `bomId`/`plannedQuantity` fields stay accepted and are internally converted to a single line (keeps `ProcurementRoutingService` and any API client working).
- `ProcurementRoutingService`: batch a sales order's manufactured shortfalls into **one** WO with N lines instead of N WOs. This is the change that delivers the actual business value — note it alters existing auto-routing behaviour, so it should be feature-flagged for one release.
- UI: `AddUpdateWorkOrder.jsx` grows a line grid (item → BOM → routing → qty). The nine detail tabs become line-scoped, with a line selector at the top of the WO detail view; `WorkOrderBasicDetails` shows header + line summary table.

*Exit:* a 3-line WO can be created, released, issued, produced and costed end to end, with three correct PRODUCE postings.

### Phase D — Split by quantity *(depends on C)*

`POST /api/production/work-order/{id}/split` with a target quantity per line.

Rules: allowed in `CREATED`/`SCHEDULED`/`MATERIAL_PENDING`/`READY_FOR_PRODUCTION`/`IN_PROGRESS`; blocked once COMPLETED/CLOSED/CANCELLED. New WO gets a fresh number from the generator. Materials re-proportion on **un-issued balance only** (trap 5). Operations are re-created and `dependsOnOperationIds` **remapped old→new** (trap 4). Original's schedule is released and both WOs re-scheduled (trap 6). Audit event `SPLIT` on both.

### Phase E — Split by line *(depends on C)*

`POST /api/production/work-order/{id}/split-lines` with the line ids to detach. Simpler than D — whole lines move with their children intact, so no re-proportioning and no dependency remapping (dependencies never cross lines). Rejected if it would empty the source WO.

### Phase F — Split by operation / subcontract *(mostly gap-closing)*

The job-work machinery already exists and works. The gaps:

- **Ad-hoc conversion**: mark an in-house `WorkOrderOperation` as subcontracted mid-flight (routing said in-house). Needs an `isSubcontracted` override on the operation, since `CostType` lives on `RoutingOperation` and must not be mutated — the routing is a shared master.
- **Blocking**: an operation with an open (`DISPATCHED`/`PARTIALLY_RECEIVED`) challan must not be completable in-house, and downstream operations must stay `WAITING_FOR_DEPENDENCY` until the challan returns.
- **Costing**: subcontract charge (`agreedRatePerUnit` × qty) must land in that line's cost pool, so it flows into the line's `realUnitCost`.

---

## Testing strategy

- **Unit** — per-line cost partitioning (the 10-pumps + 40-flanges case, asserting no bleed); dependency remap on split; issued-material split rejection; header-status derivation.
- **Integration** — 3-line WO through create→release→issue→complete, asserting three `InventoryLedger` PRODUCE rows at three distinct unit costs, and WIP netting to zero per line.
- **Migration** — V150 backfill on a copy of a populated DB: every WO ends with exactly one line; no orphaned children.
- **Regression** — the existing `nextgen-regression` Cypress pipeline must pass unchanged after Phase A and Phase B (proving no behaviour drift), then gain multi-line and split scenarios in C–E.

⚠ `WorkOrderServiceImplTest` is **already red on `master`** (12 of 18 failing: BOM-null guard and material-issue status guard). It is not a usable baseline. Phase A should start by either fixing or explicitly quarantining it — otherwise there is no way to tell a Phase-A regression from the existing breakage.

---

## Recommended sequencing

A → B → C are a single mandatory chain; there is no partial value before C. D, E, F are independent of each other and can be ordered by demand — **E is the cheapest** (whole-line moves, no re-proportioning), **F is the next cheapest** (job-work exists), **D is the most intricate** (dependency remap + issued-material rules + rescheduling).

If the goal is fastest business value: **A → B → C → E**, then D and F as pulled.

---

## Open decisions (recommendation in **bold**)

1. Header `completedQuantity` across mixed UoMs — **drop from the header, keep per line.** A single scalar summing pumps and flanges is not a meaningful number.
2. Should one line's failure block the whole WO? — **No.** Lines complete independently; the header derives `PARTIALLY_COMPLETED`.
3. Can lines share a routing/work-center booking? — **No, not in this scope.** Each line schedules independently; shared-setup optimisation is a later feature.
4. Should `ProcurementRoutingService` batching be retroactive? — **No.** New SOs only; existing open WOs stay as they are.
5. Line-level or header-level release? — **Header-level release, line-level start.** Keeps the release gate and material-request generation simple.
