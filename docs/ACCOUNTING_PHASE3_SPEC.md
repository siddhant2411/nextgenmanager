# Accounting Module — Phase 3 Implementation Spec (Perpetual Inventory & Production)

*Companion to [ACCOUNTING_MODULE_PLAN.md](ACCOUNTING_MODULE_PLAN.md) §4 and the Phase 0/1/2 specs. Planned 2026-06-18. Prereq: Phase 1 (AR/AP auto-post) + Phase 2 (GST returns) complete — invoices/bills/notes auto-post; Output/Input GST ledgers carry real balances; GSTR-3B reconciles to GL.*

> **Goal of Phase 3:** Make inventory **perpetual in the books**. Every stock movement that changes *owned* value posts a voucher automatically, so the Balance Sheet stock accounts (Raw Material / WIP / Finished Goods) tie to the `InventoryLedger` on any date. Goods receipt capitalises stock via **GR/IR clearing**; production moves value RM → WIP → FG; dispatch books **COGS**. A monthly **GL ↔ stock reconciliation report** proves the tie.

---

## Guiding principle (the CA contract)

The **`InventoryLedger` already values every movement** (qty × cost → `amount`, per `valuationMethod`). Phase 3 does **not** re-value anything and adds **no parallel stock store** — it *projects each valued movement onto the GL*. Because the GL posting reuses the exact `InventoryLedger.amount`, the reconciliation is true **by construction**:

```
Σ InventoryLedger.amount for an item-group (as of date)  ==  GL stock-account balance (2010/2011/2012)
```

This is the inventory analogue of Phase 2's "GSTR-3B ties to the GL." If a movement didn't post, the reconciliation report surfaces it.

**Perpetual replaces periodic.** Today (Phase 1) a vendor invoice books `Dr Purchases (5010E)` — periodic style. Under perpetual, **goods are capitalised at GRN** and the vendor bill clears GR/IR. Keeping both would double-count cost of goods. Phase 3 **switches** the vendor-invoice posting to GR/IR (see 3.3). This is the single most important change in the phase.

---

## Exit Criteria (Phase 3 is "done" when all true)

1. **GRN** (goods receipt) posts `Dr Raw-Material/Stock — Cr GR/IR Clearing` at the received value.
2. **Vendor invoice** with a linked GRN posts `Dr GR/IR Clearing` (not Purchases); GR/IR nets to ~zero per matched PO→GRN→Invoice. Service/expense bills (no GRN) still expense directly.
3. **Work-order consumption** posts `Dr WIP — Cr Raw-Material/Stock`; **completion** posts `Dr Finished Goods — Cr WIP`. WIP nets toward zero on a fully-completed WO.
4. **Sales dispatch** (delivery note) posts `Dr COGS — Cr Finished Goods` at the dispatched cost.
5. **Stock adjustments** post `Dr/Cr Stock ⇄ Inventory Adjustment`. **Reservation / issue-to-floor movements do NOT post** (availability/location only, no value change).
6. A **monthly GL ↔ stock reconciliation report** lists, per stock group, `InventoryLedger` value vs GL balance and flags any variance.
7. Cancelling/short-closing a WO (which writes compensating `RETURN` movements) reverses its postings through those movements — no stranded WIP.
8. Every auto-post is **idempotent** and valued identically to the `InventoryLedger` row that triggered it.

Out of scope (later phases): standard-cost variance accounting / full PPV ledgers; landed-cost apportionment (freight/duty into stock); GRN reversal UI (no GRN-cancel path exists today); revaluation runs; sub-contracting (job-work) GL beyond what consume/produce already covers.

---

## Conventions locked from the codebase (follow exactly)

- **One posting source = the inventory funnel.** All stock movements flow through `Inventory.service.InventoryTransactionServiceImpl.writeLedger(...)`. Phase 3 publishes **one event per saved `InventoryLedger` row** from there; a single accounting listener maps it. This captures GRN, consume, produce, dispatch *and* manual adjustments with zero per-document wiring.
- **Idempotency / reversal key:** `sourceDocType = "INVENTORY_MOVEMENT"`, `sourceDocId = inventoryLedger.id`. Reuses the existing `voucher` unique `(sourceDocType, sourceDocId)` index and `PostingService` replay-guard. No new table.
- Money `BigDecimal`/scale 2; reuse `PostingSupport.money(double)` to convert the funnel's primitive `amount`/`rate`. Enums `@Enumerated(STRING)`; `deletedDate`; column = field name.
- Event/listener pattern is **identical to Phase 1**: POJO event via `DomainEventPublisher`, `@TransactionalEventListener(AFTER_COMMIT)` + `@Transactional(REQUIRES_NEW)` so a posting failure logs but never rolls back the stock movement. Catch `InvalidVoucherException` as benign on replay.
- Doc-type constants live in `common/events/SourceDocTypes`; add `INVENTORY_MOVEMENT` there.
- Security: report endpoint `@RequiresAccountingAccess`. Excel via Apache POI mirroring the 1E ageing endpoints.
- **Package root:** `com.nextgenmanager.nextgenmanager.accounting.posting` (listeners) + `accounting.reports` (reconciliation).

---

## What already exists (reuse — do NOT rebuild)

| Need | Source |
|---|---|
| Valued stock movements | `InventoryLedger` (qty, rate, **amount**, closingBalance, transactionType, referenceType, referenceDocNo, valuationMethod) — written by `InventoryTransactionServiceImpl` |
| Stock GL accounts | COA seed (V128): **2010** Raw Material Stock, **2011** Work-in-Progress Stock, **2012** Finished Goods Stock, **6030** GR/IR Clearing, **5020E** COGS / Cost of Production |
| Item → stock category | `items.model.ItemType` {RAW_MATERIAL, SEMI_FINISHED, FINISHED_GOOD, SUB_CONTRACTED, CONSUMABLE} on `InventoryItem.itemType` |
| Three-way match | `VendorInvoice.purchaseOrder` (NOT NULL) + `VendorInvoice.grn` (FK → `GoodsReceiptNote`) — already wired |
| GRN flow | `GRNServiceImpl.createGRN` → `produceStock` (txnType `GRN`, refType `GRN`), header `totalAmount`, lines with `rate`/`amount` |
| WO flow | `WorkOrderServiceImpl`: consume (txnType `CONSUME`, refType `WORK_ORDER`), complete (`PRODUCE`/`WORK_ORDER`, `costPerUnit=realUnitCost`), cancel/short-close (`RETURN`/`WORK_ORDER`) |
| Dispatch / COGS | `DeliveryNoteServiceImpl` → `writeDispatchLedger` (txnType `SALES_DISPATCH`, refType `DELIVERY_NOTE`); per-item `actualCost` already computed from consumed instances |
| Existing purchase posting (to switch) | `accounting/posting/VendorInvoicePostingListener` (currently `Dr Purchases 5010E`) |
| Posting engine / helpers | `PostingService`, `LedgerResolver`, `PostingSupport`, `VoucherType` (has `JOURNAL`; add nothing) |

---

## ⚠ Two traps found in the code (must be handled)

1. **`RESERVE` and `ISSUE` change *availability*, not *ownership*.** `reserveStock` writes a `-qty` ledger row and `issueStock` writes a `qty=0` row — both move stock between *available/reserved/shop-floor* states; the goods are still owned and valued. **These must NOT post to the GL.** The listener uses an explicit **allowlist** of value-changing transaction types (below). `qty == 0` rows are skipped too.

2. **Consume/dispatch ledger rows may carry zero cost today.** `writeLedger` sets `amount = abs(qty) × costPerUnit`, but the `CONSUME` and `SALES_DISPATCH` DTOs don't reliably set `costPerUnit` (dispatch computes `actualCost` onto the `DeliveryNoteItem`, not the ledger row). If `amount` is 0, the GL posting is valueless. **Phase 3 must make `InventoryLedger.amount` authoritative for every value-changing movement** — set `costPerUnit` on the consume/dispatch DTOs (from consumed-instance cost / `actualCost`) so the funnel values the row. This is a small inventory-layer fix bundled into 3.1 and is the correctness gate for COGS/WIP.

---

## Posting contract (the allowlist)

`InventoryPostingListener` posts **only** these `transactionType` values; everything else (RESERVE, ISSUE, and any `qty==0`/`amount==0` row) is ignored. Amount = `InventoryLedger.amount`. `stock(item)` resolves the item's home stock ledger from `ItemType` (default: RAW_MATERIAL/CONSUMABLE/SUB_CONTRACTED → 2010; FINISHED_GOOD/SEMI_FINISHED → 2012).

| transactionType (refType) | Dr | Cr | Notes |
|---|---|---|---|
| `GRN` (`GRN`) | `stock(item)` | **GR/IR Clearing 6030** | Capitalise receipt; cleared by vendor invoice |
| `CONSUME` (`WORK_ORDER`) | **WIP 2011** | `stock(item)` | Component into production |
| `PRODUCE` (`WORK_ORDER`) | `stock(item)` (=FG) | **WIP 2011** | Completion at `realUnitCost` |
| `SALES_DISPATCH` (`DELIVERY_NOTE`) | **COGS 5020E** | `stock(item)` (=FG) | Cost of goods sold on dispatch |
| `ADJUSTMENT` | gain: `stock(item)` / loss: **Inventory Adjustment** | loss: `stock(item)` / gain: **Inventory Adjustment** | Sign of `quantity` decides direction |
| `RETURN` (`WORK_ORDER`) | `stock(item)` | **WIP 2011** | WO cancel/short-close → reverses the consume |

Every voucher is a 2-line entry (`Dr X / Cr Y`) at `amount`; no round-off needed (single valued amount on both sides). `VoucherType` = `JOURNAL` for all inventory postings (or a dedicated `STOCK_JOURNAL` — see open decisions).

**Vendor invoice (changed posting), per 3.3:**

| Condition | Dr | Cr |
|---|---|---|
| `grn != null` (goods bill) | **GR/IR Clearing 6030** (taxable) + Input GST | Vendor sub-ledger (grandTotal) |
| `grn == null` (service/expense bill) | Purchases 5010E (taxable) + Input GST | Vendor sub-ledger (grandTotal) — *unchanged from Phase 1* |

GR/IR residual (invoice taxable ≠ GRN value, i.e. price/qty variance) is left in 6030 and surfaced by the reconciliation report; a dedicated Purchase Price Variance ledger is **deferred**.

---

## Migration plan (V141)

Latest applied migration is **V140** (`gstreturn`). Phase 3 is light on schema — postings reuse `voucher`/`voucherline`, keyed off `InventoryLedger.id`.

| Migration | Creates / Seeds |
|---|---|
| `V141__seed_inventory_adjustment_ledgers.sql` | Seed **`5063E` Inventory Adjustment / Write-off** (EXPENSE, under DIRECT_EXPENSES or a Stock-Adjustment head) and **`4024I` Inventory Adjustment Gain** (INCOME) — referenced by the `ADJUSTMENT` posting. (2010/2011/2012/6030/5020E already exist from V128.) Follow the V128 seed style. |

> MT private fork: mirror as `V141_1__…` per the tenant-schema convention.

No table is added. If item-level ledger overrides are wanted later (open decision 4), that's an additive column on item finance settings in a future migration — not now.

---

## Work-streams (build order — each independently testable)

### 3.1 — Inventory funnel event + valuation fix  *(foundation; do first)*
- In `InventoryTransactionServiceImpl`, after each `writeLedger(...)` saves the row, publish `InventoryMovementPostedEvent(inventoryLedgerId)` via `DomainEventPublisher` — **but only for value-changing types** (or always, and let the listener filter; filtering in the listener keeps the funnel dumb — preferred).
- **Valuation fix (the 3.1 gate):** ensure `CONSUME` and `SALES_DISPATCH` DTOs set `costPerUnit` so `amount` is the true valued cost. For dispatch, pass the same per-unit cost used for `DeliveryNoteItem.actualCost`; for consume, the consumed-instance/standard cost. Add a regression assert that `InventoryLedger.amount > 0` for these.
- `accounting/posting/InventoryLedgerResolver` (or extend `LedgerResolver`): add `stock(InventoryItem)`, `wip()` (2011), `grIrClearing()` (6030), `cogs()` (5020E), `inventoryAdjustment()` (5063E), `inventoryAdjustmentGain()` (4024I).

### 3.2 — `InventoryPostingListener`  *(depends on 3.1)*
- `@TransactionalEventListener(AFTER_COMMIT)` + `REQUIRES_NEW`. Loads the `InventoryLedger` row, applies the **allowlist** + `qty==0`/`amount==0` skip, builds the 2-line draft per the posting contract, posts with `sourceDocType=INVENTORY_MOVEMENT`, `sourceDocId=ledger.id`.
- Voucher `date = movementDate`; narration includes `transactionType` + `referenceDocNo`.
- Idempotent on replay (catch `InvalidVoucherException`).

### 3.3 — Switch vendor-invoice posting to GR/IR  *(depends on 6030; coordinate with 3.1)*
- Modify `VendorInvoicePostingListener`: if `inv.getGrn() != null` → debit **GR/IR Clearing 6030** instead of `purchasesRawMaterial()`; else keep `Dr Purchases 5010E` (services/expenses). Tax + vendor lines unchanged.
- Update its unit test for the GR/IR branch; keep the existing periodic test for the no-GRN branch.
- **Sequencing note:** 3.1 (GRN now debits stock) and 3.3 (invoice now clears GR/IR) must land **together** — until both are in, GR/IR won't net. Build behind the same change set.

### 3.4 — GL ↔ Stock reconciliation report  *(read-only; depends on postings existing)*
- `accounting/reports/StockGlReconciliationService`: for a date, per stock group (RM/WIP/FG), compute (a) `Σ InventoryLedger.amount` for items in that `ItemType` group as of date and (b) GL balance of 2010/2011/2012; row = {group, stockValue, glBalance, variance}. `tiesOut = |variance| ≤ ₹1` per group.
- Also a **GR/IR open-items** section: balance of 6030 = goods received not invoiced (or vice-versa).
- Endpoint `GET /api/accounting/reports/stock-gl-reconciliation?asOf=` (+ `/excel`). Mirrors the 1E ageing endpoint shape.

### 3.5 — Frontend  *(after backend green)*
- Under `ui/src/components/accounting/reports/`: **Stock vs GL Reconciliation** page (group table with green/amber tie banner like GSTR-3B's recon banner) + Excel download; service fn in `accountingReportService.js`; route in `AccountingPage.jsx` + nav entry in `config/modules.js` Reports.
- Auto-posted stock vouchers are already visible in Day Book / Ledger / Trial Balance from Phase 0 — no new voucher UI.

---

## Reversal model (no special listener needed)

Unlike Phase 1 AR/AP (which needed `SourceDocReversalListener` on cancel), inventory **reverses through compensating movements**: a WO cancel/short-close writes `RETURN` rows, a stock correction writes a negative `ADJUSTMENT` — each is itself a valued `InventoryLedger` row that posts its own reversing voucher. So perpetual postings self-correct.
**Known gap (not Phase 3):** `GRNServiceImpl` has no cancel/delete path, so a wrong GRN can't be reversed today. When a GRN-reversal flow is added, it will write a compensating movement and post automatically — no accounting change required.

---

## Testing strategy (Phase 3)

- **Unit (`InventoryPostingListener`):** GRN → Dr stock/Cr GR/IR; CONSUME → Dr WIP/Cr stock; PRODUCE → Dr FG/Cr WIP; SALES_DISPATCH → Dr COGS/Cr FG; ADJUSTMENT gain vs loss direction; **RESERVE/ISSUE/qty==0 → no voucher** (the allowlist guard); replay idempotency.
- **Unit (`VendorInvoicePostingListener`):** grn present → GR/IR branch; grn null → Purchases branch (regression).
- **Unit (`StockGlReconciliationService`):** seeded movements → stockValue == glBalance per group; inject a missing post → `tiesOut=false`.
- **Integration:** receive (GRN) → bill (vendor invoice) → GR/IR nets ~0; issue+consume+complete a WO → WIP nets ~0, FG carries cost; dispatch → COGS booked, FG reduced; Trial Balance still ties; reconciliation ties per group.

---

## Recommended sequencing

**3.1 (funnel event + valuation fix) → 3.2 (`InventoryPostingListener`) + 3.3 (GR/IR switch) together → 3.4 (reconciliation) → 3.5 (UI).** 3.1 is the critical path and the highest-risk piece (valuation correctness); it gets the most test coverage. 3.2 and 3.3 must ship together so GR/IR nets. 3.4 is the proof and can be built in parallel with the UI stub.

---

## Opening stock & mid-stream migration (added 2026-06-18, built)

Perpetual inventory is only adoptable mid-stream if a company can bring **opening stock** in cleanly — otherwise the books never tie on day one. Two pieces, both built:

1. **Opening stock posts to equity, not income.** Inventory already has a per-item opening-stock loader (`ProcurementDecision.OPENING_STOCK` in `InventoryInstanceServiceImp.addInventory`), which writes an `InventoryLedger` row with `transactionType=ADJUSTMENT` + `referenceType=OPENING_STOCK`. That write path now publishes `InventoryMovementPostedEvent` **only for `OPENING_STOCK`** (other receipts through it are unchanged). The listener routes `referenceType=OPENING_STOCK` to **`Dr stock(item) / Cr Opening Balance Equity (3022E)`** — *not* Inventory Adjustment Gain (income). So one per-item load seeds the inventory subledger **and** the GL stock accounts in a single balanced step. New ledger seeded in **V142**. `Opening Balance Equity` is the temporary cutover bridge; once the full opening trial balance is entered it nets to capital (the QuickBooks model).
   - **Coordination caveat:** load opening **stock** via the OPENING_STOCK loader (it posts the stock + equity legs itself). Do *not* also enter stock lines in the Phase-0 GL opening-balance Excel import, or stock doubles. Use the GL opening-balance import for the **non-stock** opening balances (debtors, creditors, bank, …), crediting/debiting Opening Balance Equity as the contra.

2. **Cutover-aware reconciliation.** `reconcile(from, asOf)` takes an optional cutover/`from` date (UI field "Cutover (from)"). When set, both sides are computed over the window `[from, asOf]` — GL via `movementByCodeInRange`, stock via `movementsForReconciliationInRange` — so pre-migration history (legacy, often un-costed `CONSUME`/`DISPATCH` rows) is excluded and a mid-stream adoption can still tie. When null, the all-time view (cumulative GL vs full replay).

**Why whole-history looked wrong before this:** on legacy data the all-time replay shows negative WIP and undiminished FG because historical `CONSUME`/`DISPATCH` rows carry zero cost (the valuation fix applies to new movements only). Set the cutover date to go-live to get a clean, tying view.

## Open decisions (recommendation in **bold**)

1. **Posting source — funnel event vs per-document events.** **Recommend the single funnel event** (`InventoryMovementPostedEvent` from `InventoryTransactionServiceImpl`): one hook covers every valued movement incl. manual adjustments, and GL ties to `InventoryLedger` by construction. Per-document events would miss ad-hoc adjustments and duplicate wiring.
2. **Voucher type for stock postings.** **Recommend reusing `JOURNAL`** (no migration, no enum/CHECK change) vs adding a dedicated `STOCK_JOURNAL`/`MANUFACTURING` type for cleaner Day-Book filtering. JOURNAL now; add a type later if reporting wants it.
3. **Item → stock-ledger mapping.** **Recommend the `ItemType` default** (RM/CONSUMABLE/SUB_CONTRACTED → 2010; FINISHED_GOOD/SEMI_FINISHED → 2012; WIP 2011 only as the WO bridge). Per-item ledger override deferred. Confirm SEMI_FINISHED → Finished Goods (vs its own stock head).
4. **GR/IR price/qty variance.** **Recommend leaving the residual in 6030** and surfacing it in the reconciliation report this phase; a dedicated Purchase Price Variance ledger + apportionment is deferred to a later phase.
5. **Overhead absorption on completion.** **Recommend posting FG at the WO's `realUnitCost` as-is** (whatever it already includes) — `Dr FG / Cr WIP` — and deferring separate labour/overhead-absorption ledgers until the production-costing model is richer. Confirm `realUnitCost` already rolls in overhead, else FG is undervalued.
