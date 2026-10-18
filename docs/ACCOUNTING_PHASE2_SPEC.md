# Accounting Module — Phase 2 Implementation Spec (GST Returns)

*Companion to [ACCOUNTING_MODULE_PLAN.md](ACCOUNTING_MODULE_PLAN.md) §7 and [ACCOUNTING_PHASE0_SPEC.md](ACCOUNTING_PHASE0_SPEC.md). Planned 2026-06-14. Prereq: Phase 1 (AR/AP automation) complete — invoices/bills/notes auto-post to GL; Output/Input GST ledgers carry real balances.*

> **Goal of Phase 2:** Generate **file-ready GST returns straight from the books**. GSTR-1 (outward) and GSTR-3B (summary) are produced from the documents that already carry GST, and **GSTR-3B must reconcile to the GL** — the tax in the return ties to the Output/Input GST ledger movement for the period, or the report flags the variance. Filing a return **locks** the period.

---

## Guiding principle (the CA contract)

The **General Ledger is the source of truth.** GST registers and returns are *projections* over the existing source documents (`TaxInvoice`, `SalesCreditNote`, `VendorInvoice`, `DebitNote`) — Phase 2 adds **no parallel tax store**. The reconciliation is non-negotiable:

```
Σ Output CGST/SGST/IGST ledger movement (period)  ==  GSTR-3B outward tax (3.1)
Σ Input  CGST/SGST/IGST ledger movement (period)  ==  GSTR-3B eligible ITC (4)
```

If a document's tax didn't post to the GL (Phase 1 failure), it won't tie — and that's the point: the return surfaces it.

---

## Exit Criteria (Phase 2 is "done" when all true)

1. **Outward register** and **Inward/ITC register** render for any month, line-itemised, and tie to the source documents.
2. **GSTR-1** preview classifies every outward document into the correct table (B2B / B2CL / B2CS / CDNR / CDNUR / EXP / HSN / Docs) and exports **Excel + GSTN offline-tool JSON**.
3. **GSTR-3B** auto-computes outward liability + eligible ITC + net payable, shows a **GL reconciliation panel** (return vs ledger, with variance), and exports Excel + JSON.
4. **HSN/SAC summary** computes from invoice lines (qty, taxable, rate-wise tax).
5. **Filing a return locks** its `AccountingPeriod` (OPEN → LOCKED); posting into a locked period is already rejected (Phase 0). Unlock requires `ROLE_ACCOUNTS_HEAD`.
6. A **`gstreturn` record** captures each filing (period, type, status, totals, JSON snapshot) — immutable once FILED.

Out of scope (later phases): GSTR-2B reconciliation, GSTR-9 annual, e-invoice/IRN, e-way bill (all Phase 6); TDS/TCS (Phase 4).

---

## Conventions locked from the codebase (follow exactly)

- **No new tax tables for registers** — compute by querying source docs in a date range. Persist only the **filing snapshot**.
- Money `BigDecimal`/`NUMERIC(12,2)`; enums `VARCHAR + CHECK`, `@Enumerated(STRING)`; `BIGSERIAL`/`IDENTITY`; `deletedDate` soft delete; column = field name.
- **Excel:** Apache POI (`XSSFWorkbook`), mirror `InventoryItemExportService`; controller streams `ResponseEntity<byte[]>` with `spreadsheetml.sheet` + `Content-Disposition` (see `AccountingReportController` ageing endpoints from 1E).
- **JSON:** existing Jackson `ObjectMapper`; match the **GSTN offline-tool schema** field names exactly (`gstin`, `inum`, `idt`, `val`, `pos`, `rt`, `txval`, `iamt`, `camt`, `samt`, `csamt`, …).
- **Security:** `@RequiresAccountingAccess`; filing/lock actions `ROLE_ACCOUNTS_HEAD`.
- **Package root:** `com.nextgenmanager.nextgenmanager.accounting.gst`.

---

## What already exists (reuse — do NOT rebuild)

| Need | Source |
|---|---|
| Company GSTIN + home state | `CompanyDetails.gstNumber`, `.stateCode`, `.financialYearStartMonth` |
| Party GSTIN / state / type | `Contact.gstNumber`, `.stateCode`, `.gstType` (REGULAR/COMPOSITION/…), `.panNumber` |
| Intra vs inter-state | `purchase.service.GstResolver.deriveGstTreatment(Contact)` → `GstTreatment` |
| Outward tax data | `TaxInvoice` (cgst/sgst/igst/taxableValue, → salesOrder.customer), `InvoiceItem` (per-line tax), `SalesCreditNote` |
| Inward tax data | `VendorInvoice` (cgst/sgst/igst/cess/subtotal, vendor), `VendorInvoiceItem` (per-line tax + hsnCode), `DebitNote` |
| HSN/SAC | `InventoryItem.hsnCode` |
| Output/Input GST ledger balances | COA `9010/9011/9012/9013` (output), `6020/6021/6022/6023` (input), `9014` RCM payable |
| Period status | `AccountingPeriod` (OPEN/LOCKED/CLOSED), `FinancialYearService.lockPeriod/unlockPeriod` |
| Ledger movement query | `VoucherLineRepository.aggregateByAccount(asOf)` (extend for date-range per ledger) |

**Derivations (no field exists, compute):**
- **B2B vs B2C:** customer `gstNumber` present → B2B; else B2C.
- **Place of supply:** customer `stateCode` (else first 2 chars of GSTIN).
- **B2CL vs B2CS:** inter-state B2C with invoice value > ₹2,50,000 → B2CL (per-invoice); else B2CS (consolidated by POS + rate).
- **Export:** `Contact.gstType` ∈ {EXPORT/SEZ…} or treatment — **deferred** (see open decisions); domestic first.

---

## Migration plan (V136 → V137)

| Migration | Creates |
|---|---|
| `V136__create_gst_return.sql` | `gstreturn` (id, financialYear_id, period_id FK, returnType `VARCHAR(10)` CHECK GSTR1/GSTR3B, status DRAFT/FILED, periodLabel, totals: taxableValue/cgst/sgst/igst/cess/total, payload `TEXT` (JSON snapshot), filedBy, filedDate, creationDate, updatedDate, deletedDate). UNIQUE(period_id, returnType) WHERE deletedDate IS NULL |
| `V137__vendorinvoice_itc_flags.sql` | Add `itcEligible BOOLEAN DEFAULT true`, `itcIneligibleReason VARCHAR(120)`, `reverseCharge BOOLEAN DEFAULT false` to `vendorInvoice` (drives 3B eligible-ITC vs blocked credits / RCM). Backfill existing rows `itcEligible=true` |

Registers, GSTR-1, GSTR-3B, HSN need **no tables** — they are queries. `V137` is only needed if ITC-eligibility (open decision 3) is in this phase.

---

## Work-streams (build order — each independently testable)

### 2.1 — GST Registers *(read projections; no dependencies)*
`gst/register/` — `GstRegisterService`:
- `outwardRegister(from, to)` → rows from `TaxInvoice` + `SalesCreditNote`: gstin, partyName, POS, docNo, docDate, hsn, taxableValue, rate, cgst, sgst, igst, cess, docType (INV/CRN).
- `inwardRegister(from, to)` → rows from `VendorInvoice` + `DebitNote`: same shape + `itcEligible`, RCM flag.
- DTOs `OutwardRegisterRow`, `InwardRegisterRow`, `GstRegisterDto{period, rows, totals}`.
- Endpoints `GET /api/accounting/gst/register/{outward|inward}?from&to` (+ `/excel`).

### 2.2 — GSTR-1 builder *(depends on 2.1)*
`gst/gstr1/` — `Gstr1Builder.build(from, to)` classifies outward docs:

| Table | Rule | Granularity |
|---|---|---|
| **B2B** | customer has GSTIN | per invoice, per rate |
| **B2CL** | inter-state, no GSTIN, value > 2.5L | per invoice |
| **B2CS** | other B2C | consolidated by POS + rate |
| **CDNR** | credit/debit note to registered party | per note |
| **CDNUR** | credit/debit note to unregistered | per note |
| **HSN** | all lines | by HSN + rate (qty, taxable, tax) |
| **DOCS** | invoice/note number ranges issued | per series (gap detection from voucher numbering) |

- `Gstr1Dto` mirrors offline-tool sections; `Gstr1ExcelService` (POI, one sheet per table) + `Gstr1JsonService` (offline-tool schema).
- Endpoints `GET /api/accounting/gst/gstr1?from&to` (preview JSON), `/gstr1/excel`, `/gstr1/json`.

### 2.3 — GSTR-3B builder + **GL reconciliation** *(depends on 2.1, GL)*
`gst/gstr3b/` — `Gstr3bBuilder.build(from, to)`:
- **3.1** outward taxable supplies (from outward register, split intra/inter).
- **3.1(d)** inward RCM (from inward register `reverseCharge`).
- **4** eligible ITC (inward register where `itcEligible`), split into ITC available / reversed / ineligible.
- **5/6** net tax payable = output − eligible ITC.
- **Reconciliation:** query Output 9010-13 and Input 6020-23 ledger movement for the period; compare to 3B figures; `Gstr3bDto.reconciliation` carries `{ledgerOutput, returnOutput, outputVariance, ledgerItc, returnItc, itcVariance, tiesOut:boolean}`.
- Excel + JSON export. Endpoint `GET /api/accounting/gst/gstr3b?from&to` (+ `/excel`, `/json`).

### 2.4 — HSN summary *(reused by 2.2; standalone endpoint too)*
`gst/gstr1/HsnSummaryService` — aggregate `InvoiceItem`/`SalesCreditNoteItem` by `hsnCode` + rate → qty, taxableValue, cgst, sgst, igst, cess. Endpoint `GET /api/accounting/gst/hsn-summary?from&to` (+ Excel).

### 2.5 — Return filing + period lock *(depends on all above)*
`gst/filing/` — `GstReturnService`:
- `fileGstr1(periodId)` / `fileGstr3b(periodId)` → builds the return, persists a `gstreturn` snapshot (status FILED, JSON payload + totals), then `FinancialYearService.lockPeriod(periodId)` (3B filing locks; GSTR-1 may lock or be a soft milestone — see open decisions).
- `getFilings(fy)`, `getReturn(id)` (returns snapshot). Re-filing a FILED period requires unlock (`ROLE_ACCOUNTS_HEAD`).
- Endpoints `POST /api/accounting/gst/file/{gstr1|gstr3b}/{periodId}`, `GET /api/accounting/gst/returns?fy=`.

### 2.6 — Frontend *(after backend green)*
Under `ui/src/components/accounting/gst/`, registered in `AccountingPage` routes + `config/modules.js` (new **GST** nav section):
- **GSTR-1** preview (tabbed by table) + Excel/JSON download.
- **GSTR-3B** summary with the **reconciliation banner** (green "ties to GL" / red variance) + export.
- **HSN Summary** page. **Registers** (outward/inward) pages. **Returns log** (filed history, lock status).
- Service `gstService.js`.

---

## Testing strategy (Phase 2)

- **Unit (`Gstr1Builder`):** B2B vs B2CS classification by GSTIN; B2CL threshold; CDNR from credit note; HSN aggregation across rates.
- **Unit (`Gstr3bBuilder` reconciliation):** seed invoices → assert 3B outward == Output-ledger movement; mismatch → `tiesOut=false`.
- **Unit (`GstReturnService`):** filing persists snapshot + locks period; re-file blocked while LOCKED.
- **Integration:** post a month of sales/purchase → GSTR-1 totals == GSTR-3B 3.1 == Output ledger; ITC == Input ledger.

---

## Recommended sequencing

**2.1 registers → 2.4 HSN → 2.2 GSTR-1 → 2.3 GSTR-3B (+reconciliation) → 2.5 filing/lock → 2.6 UI.** GSTR-3B reconciliation is the critical correctness gate; 2.1/2.4 are pure reads and can be parallelised. Excel before JSON in each (preview value first, offline-tool schema is fiddlier).

---

## Decisions (LOCKED 2026-06-14)

1. **Export format priority:** ✅ **Excel/preview first, GSTN offline-tool JSON second.** Build and validate the Excel for each return, then encode the offline-tool JSON.
2. **GSTR-1 filing & lock:** ✅ **3B filing locks the period; GSTR-1 filing is recorded but does NOT hard-lock.** (Mirrors real flow — GSTR-1 filed first, 3B finalises the month.)
3. **ITC eligibility (V137):** ✅ **Add `itcEligible` + `reverseCharge` flags now, default `itcEligible=true`.** Drives a correct 3B section 4 (eligible ITC vs blocked credits) and the RCM split.
4. **Reverse charge (RCM):** ✅ **Model the flag now; keep RCM computation minimal in 3B** — surface RCM inward supplies and rely on existing `9014 RCM Payable` posting. Full RCM self-invoicing **deferred** to a later phase.
5. **Exports / SEZ:** ✅ **Deferred** — domestic B2B/B2C first; EXP/SEZ GSTR-1 tables stubbed empty. Revisit when TaxInvoice gains export-type + shipping-bill fields.
