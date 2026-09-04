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

## Deliberately not in step 1

- `NonConformanceReport` and its dispositions (rework / scrap / use-as-is / return to vendor).
- `QualityStatus` on `InventoryInstance`, and GRN rejected quantity routing into the QUARANTINE
  warehouse that phase F created and nothing yet uses.
- The PACKAGE source, which has no document to hang off until phase J.

Those are step 2 (V170). Step 1 is the model, the lot lifecycle, and the gate — the part that
changes behaviour rather than adding storage.

## Migrations

`V169` — `inspectionlot`, `inspectionresult`, and `productinventorysettings.finalInspectionRequired`.
`V170` — non-conformance reports, instance quality status, quarantine routing.
