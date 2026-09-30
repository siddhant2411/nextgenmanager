# Phase H — QC as a gate, not a log

Today a work order whose QA parameters failed still completes, and `produceStock` still puts the
finished goods on the shelf. Quality is recorded and then ignored. This phase makes inspection
capable of stopping a movement.

## What already exists (and is not being rebuilt)

- `BomQaParameter` — the parameter definitions on a BOM: name, type, min/max, unit, critical.
- `WorkOrderQaEntry` — those parameters copied onto a **work order operation** when it is created.
- `WorkOrderQaResult` — what the operator measured: actual value, `QaResult` PENDING/PASS/FAIL.
- `RejectionEntry` + `RejectionReasonCode` — rejections booked during production.
- `QualityStatus` (PENDING_QC / PASSED / FAILED / WAIVED) — declared, but only carried by
  `BatchNumber` and `SerialNumber`.

In-process inspection is therefore already modelled, at the operation level, and this phase leaves
that data entry alone. What is missing is a **verdict that something acts on**.

## The model

`InspectionLot` is a header: *this quantity of this item, arising from this document, was
inspected, and here is the verdict*. Four sources, matching the four points goods can be stopped:

| Source | Raised against | Blocks |
|---|---|---|
| `INCOMING` | a GRN line | receipt into free stock (step 2) |
| `IN_PROCESS` | a work order operation | nothing on its own — rolls up existing QA results |
| `FINAL` | a work order | `completeWorkOrder` → `produceStock` |
| `PACKAGE` | a packing slip (phase J) | closing the slip |

**Results are stored once.** An `IN_PROCESS` lot does not copy the operator's measurements; it
rolls up the `WorkOrderQaResult` rows already hanging off the operation. Only sources with no
parameter store of their own — INCOMING, FINAL, PACKAGE — carry `InspectionResult` rows. Two
places to enter the same measurement is exactly the "second checklist" this phase is meant to
avoid.

## The gate

`completeWorkOrder` refuses to proceed when any of these hold:

1. **A FINAL lot exists and has FAILED.** No configuration; strictly better than today.
2. **The item requires final inspection and no passed FINAL lot exists.** Gated on a new
   `finalInspectionRequired` flag, default `false`, so the rule bites only where someone has
   turned it on. Rolling this out to every item at once would stop a shop floor that has never
   entered an inspection in its life.
3. **A critical operation parameter failed.** `WorkOrderQaResult` with `result = FAIL` on an entry
   marked `critical` — the literal hole this phase was raised to close.

A `WAIVED` lot passes the gate. Waivers go through the existing `common/approval` engine rather
than a new one, and the lot records who waived it.

## Step 2 (V170) — what happens to goods that failed

- **`NonConformanceReport`**, raised against a lot that failed, closed by a disposition: rework,
  scrap, use-as-is, return to vendor. Use-as-is needs an approver's name, for the same reason a
  waiver needs a reason — it overrides an inspection rather than acting on one.
- **`QualityStatus` on `InventoryInstance`.** It existed only on batch and serial records, so
  untracked stock had no way to say it had failed. Existing rows become PASSED: every unit already
  in stock was accepted into it, and marking them all as awaiting QC would strand real stock.
- **Rejected goods stop vanishing.** A GRN's rejected quantity was typed in and dropped — only the
  accepted quantity produced stock. It is now received into the QUARANTINE warehouse, marked
  FAILED. Where no quarantine warehouse exists it goes to the default one, still FAILED, with a
  loud log: quality status is what keeps it off a pick, and refusing the receipt over a missing
  master record would stop goods at the door.
- **Picking refuses unfit stock.** `PickListServiceImpl` will not allocate an instance that is
  FAILED or PENDING_QC. Without this the column would be storage, not a gate.

What step 2 does **not** do is move quantities. Scrapping goods and returning them to a vendor are
stock movements with their own documents — a write-off adjustment and a debit note — and inventing
them here would put movements in the ledger that nothing explains. The stock stays FAILED, off
every pick, until one of those is raised.

The PACKAGE source still waits for phase J, which is the only thing that gives it a document to
hang off.

## Migrations

`V169` — `inspectionlot`, `inspectionresult`, and `productinventorysettings.finalInspectionRequired`.
`V170` — non-conformance reports, instance quality status, quarantine routing.
