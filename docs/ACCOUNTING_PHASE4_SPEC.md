# Accounting Module — Phase 4 Implementation Spec (TDS, Payroll JV & Depreciation)

*Companion to [ACCOUNTING_MODULE_PLAN.md](ACCOUNTING_MODULE_PLAN.md) §8 and the Phase 0–3 specs. Planned 2026-06-20. Prereq: Phases 1–3 complete — AR/AP, GST returns, and perpetual inventory all auto-post; GL is the source of truth and reconciles to GSTR-3B and to the InventoryLedger.*

> **Goal of Phase 4:** Put **statutory deductions** in the books. Tax Deducted at Source (TDS) on vendor payments is withheld, tracked deductee-wise, deposited via challan, and exported for the **26Q/27Q** quarterly return. Monthly **payroll** and **depreciation** enter the GL as structured journal vouchers so salary/PF/ESI/PT/TDS liabilities and asset depreciation are on the books — without building full Payroll or Fixed-Asset modules yet.

---

## Locked decisions (user-approved 2026-06-20 — do not re-litigate)

| # | Decision | Choice | Consequence |
|---|---|---|---|
| 1 | **TDS deduction point** | **At payment** (on `VendorPayment`) | Only `VendorPaymentPostingListener` changes. No TDS fields on `VendorInvoice`; invoice posting untouched. Matches the Phase-1 locked plan. |
| 2 | **TCS on sales (206C(1H))** | **Deferred** | No per-buyer ₹50L threshold engine, no TCS ledger, no TCS in Phase 4. Revisit as a 4.x add-on when a buyer actually crosses the limit. |
| 3 | **Payroll** | **Light monthly JV** | A structured payroll-summary voucher. **No** payroll engine (no payslips/attendance/per-employee statutory computation). The `employee` module stays masters-only. |
| 4 | **Depreciation** | **Manual JV** | A thin depreciation-entry form posting `Dr Depreciation / Cr Accumulated Dep`. **No** Fixed-Asset register, no SLM/WDV auto-computation. The `assets` module (machine-ops) is untouched. |

---

## Exit Criteria (Phase 4 is "done" when all true)

1. A **`TdsSection`** master exists and is seeded with the common resident sections (194C/194J/194Q/194I/194H/194A), each with rate, PAN-missing rate (20%), and thresholds.
2. Recording a **vendor payment** for a vendor with a PAN + applicable section withholds TDS: the vendor account is settled at the gross amount, the bank pays the **net**, and **TDS Payable (9015)** is credited.
3. Each deduction creates a **`TdsEntry`** (deductee-wise: section, contact, taxable base, TDS, FY, quarter, `DEDUCTED`).
4. A **`TdsChallan`** records a deposit, flips its linked entries to `DEPOSITED`, and posts `Dr TDS Payable / Cr Bank`.
5. **26Q** (resident, non-salary) deductee-wise data exports to Excel for the quarter; company **TAN** is captured.
6. A **monthly payroll JV** posts `Dr Salaries & Wages / Cr Salary Payable, PF, ESI, PT, TDS Payable` and balances.
7. A **depreciation JV** posts `Dr Depreciation / Cr Accumulated Depreciation`.
8. A **TDS register / deductee summary** report lists deductions by section and party with deposited-vs-pending status.

Out of scope (later): TCS 206C(1H); TDS-at-invoice-booking; FVU/NSDL `.txt` return format & Form 16A PDF (Excel data only here); full payroll engine; Fixed-Asset register with SLM/WDV runs; 24Q salary-TDS return detail; lower-deduction certificates (197).

---

## Conventions locked from the codebase (follow exactly)

- **Posting pattern is identical to Phases 1–3:** POJO event via `DomainEventPublisher`, `@TransactionalEventListener(AFTER_COMMIT)` + `@Transactional(REQUIRES_NEW)`; a posting failure logs but never rolls back the business doc. Catch `InvalidVoucherException` as benign on replay. Idempotency via voucher `(sourceDocType, sourceDocId)`.
- TDS is **not a new posting trigger** — it rides the **existing** `VendorPaymentMadeEvent` / `VendorPaymentPostingListener`. The listener gains a TDS branch; no new listener for the deduction itself.
- Money `BigDecimal` scale 2; reuse `PostingSupport` (`dr`/`cr`/`nz`/`money`). Enums `@Enumerated(STRING)`; soft delete `deletedDate`; column = field name (`PhysicalNamingStrategyStandardImpl`).
- Doc-type constants in `common/events/SourceDocTypes` — add `TDS_CHALLAN`, `PAYROLL`, `DEPRECIATION`.
- New package root: `com.nextgenmanager.nextgenmanager.accounting.tds`. Payroll/Depreciation JV live under `accounting.voucher` (they are structured vouchers, not a domain).
- Security: TDS read endpoints `@RequiresAccountingAccess`; challan creation + payroll/dep posting `@RequiresAccountsHead` (mirrors GST filing). Excel via Apache POI like the 1E/2.x exports.
- Quarter = Indian FY quarter: Q1 Apr–Jun, Q2 Jul–Sep, Q3 Oct–Dec, Q4 Jan–Mar.

---

## What already exists (reuse — do NOT rebuild)

| Need | Source |
|---|---|
| Deductee PAN | `contact.model.Contact.panNumber` (10-digit, already present) |
| TDS ledgers | COA seed (V128): **9015** TDS Payable, **6010** TDS Receivable |
| Payroll ledgers | **5030E** Salaries & Wages, **9031** Salary Payable, **9017** PF Payable, **9018** ESI Payable, **9019** Professional Tax Payable, **9015** TDS Payable |
| Depreciation ledgers | **5070E** Depreciation, **1020** Accumulated Depreciation |
| Vendor payment doc | `purchase.model.VendorPayment` + `VendorPaymentMadeEvent` + `VendorPaymentServiceImpl.recordPayment` + `VendorPaymentController` |
| Vendor-payment posting | `accounting/posting/VendorPaymentPostingListener` (currently `Dr Vendor / Cr Bank` for full amount) |
| Posting engine / helpers | `PostingService`, `LedgerResolver`, `PostingSupport`, `VoucherType` (`PAYMENT`, `JOURNAL`, `PAYROLL` already in the enum) |
| Intra/inter (n/a for TDS, kept for reference) | `purchase.service.GstResolver` |

**Gap to add:** company **TAN** is not on `CompanyDetails` (TAN belongs to the deductor, not the deductee `Contact`). Add `tan` in 4B for the 26Q export header.

---

## Migrations

Payroll and depreciation are pure JVs over **existing** ledgers, so they need **no migrations**. Phase 4 = **V143–V144** only:

- **V143** — `tds_section` table + seed; `ALTER vendorPayment ADD tdsSection_id, tdsAmount, tdsRate`; `tds_entry` table.
- **V144** — `tds_challan` table; `ALTER companyDetails ADD tan`.

(Roadmap reserved V143–V145; V145 is unused — payroll/dep need no schema. Latest migration after Phase 4 = **V144**.)

---

## 4A — TDS master + deduction at payment (V143)

### `tds/model/TdsSection` (master)
Fields: `id`, `section` (e.g. `194C`), `description`, `rate` (BigDecimal %), `panMissingRate` (default 20%), `thresholdSingle` (per-transaction), `thresholdAnnual` (cumulative/FY), `applicableTo` (`VENDOR_PAYMENT` for now), `active`, `deletedDate`.

**Seed (V143)** — common resident sections:

| section | description | rate | panMissing | single | annual |
|---|---|---|---|---|---|
| 194C | Payment to contractors (company) | 2% | 20% | 30,000 | 1,00,000 |
| 194C-IND | Payment to contractors (ind/HUF) | 1% | 20% | 30,000 | 1,00,000 |
| 194J | Professional / technical fees | 10% | 20% | 30,000 | — |
| 194Q | Purchase of goods > ₹50L | 0.1% | 5% | — | 50,00,000 |
| 194I-P | Rent — plant & machinery | 2% | 20% | 2,40,000 | — |
| 194I-B | Rent — land/building | 10% | 20% | 2,40,000 | — |
| 194H | Commission / brokerage | 5% | 20% | 15,000 | — |
| 194A | Interest (non-bank) | 10% | 20% | 5,000 | — |

Thresholds are advisory in v1 (surfaced as a UI hint); deduction amount is taken from what the user confirms on the payment. Auto-threshold enforcement is a later hardening.

### `VendorPayment` additions (V143 ALTER)
`tdsSection_id` (FK → tds_section, nullable), `tdsRate` (BigDecimal), `tdsAmount` (BigDecimal, default 0).

**Amount convention (critical — state in UI):** `VendorPayment.amount` = the **gross** amount settled against the invoice (this is what debits the vendor and clears the bill). `tdsAmount` = withheld. **Net bank outflow = amount − tdsAmount.** So a ₹1,00,000 bill settled with 2% 194C: `amount=1,00,000`, `tdsAmount=2,000`, vendor receives ₹98,000.

### `tds/model/TdsEntry` (deductee ledger)
Fields: `id`, `section_id` (FK), `contact_id` (FK deductee), `sourceDocType` (`VENDOR_PAYMENT`), `sourceDocId`, `taxableAmount`, `tdsAmount`, `rate`, `financialYear` (String e.g. `2025-26`), `quarter` (`Q1`–`Q4`), `deductionDate`, `status` (`DEDUCTED`/`DEPOSITED`), `challan_id` (FK, null until deposited), `deletedDate`.

Created in the **same flow** as the payment (service-side, not a listener) so the entry exists transactionally with the payment. Idempotent per `(sourceDocType, sourceDocId)`.

### Posting — extend `VendorPaymentPostingListener`
Add a TDS branch to the existing listener. With `tds = nz(payment.getTdsAmount())`:

| Condition | Dr | Cr |
|---|---|---|
| `tds == 0` (today's behaviour) | Vendor (amount) | Bank/Cash (amount) |
| `tds > 0` | Vendor (amount) | Bank/Cash (**amount − tds**) + **TDS Payable 9015** (tds) |

Three-line balanced entry when TDS applies. `LedgerResolver` gains `tdsPayable()` → **9015**. Everything else (idempotency, AFTER_COMMIT/REQUIRES_NEW, vendor sub-ledger resolution) is unchanged.

---

## 4B — Challan deposit + 26Q export (V144)

### `tds/model/TdsChallan`
Fields: `id`, `challanNumber` (BSR-CIN / challan serial), `bsrCode`, `depositDate`, `amount`, `section` (or null for multi-section), `financialYear`, `quarter`, `paymentMode`, `bankAccount` ledger ref, `notes`, audit, `deletedDate`.

**Flow:** select pending `TdsEntry` rows (status `DEDUCTED`) for a quarter → create challan for their total → entries flip to `DEPOSITED` with `challan_id` set → publish `TdsChallanDepositedEvent`.

### Posting — `TdsChallanPostingListener` (new, Phase-1 pattern)
`sourceDocType = TDS_CHALLAN`, `sourceDocId = challan.id`, VoucherType `PAYMENT`:

| Dr | Cr |
|---|---|
| **TDS Payable 9015** (amount) | Bank (amount) |

This clears the liability accrued at deduction. 9015 nets to ~zero per fully-deposited quarter (the TDS analogue of GR/IR netting).

### Company TAN + 26Q export
- `ALTER companyDetails ADD tan` (10-char). Surfaced in CompanyDetails settings UI.
- **26Q export** (`tds/export/Tds26QExportService`, POI): deductee-wise rows for a quarter — deductor TAN/name, deductee PAN/name, section, date of payment, amount paid (taxable), TDS, challan no/date/BSR. Excel only in Phase 4; FVU `.txt` deferred to Phase 6.
- 27Q (non-resident) shares the export with a residency filter; minimal in v1.

---

## 4C — Payroll JV (no migration)

A **structured monthly journal**, not an engine. Endpoint `POST /api/accounting/vouchers/payroll` takes a summary: `month`, `grossSalary`, `employeePf`, `employerPf`, `esi`, `professionalTax`, `tds`, optional cost-center.

Posting (VoucherType `PAYROLL`, `sourceDocType = PAYROLL`, `sourceDocId` = a synthetic month key so it's idempotent per month):

| Dr | Cr |
|---|---|
| **Salaries & Wages 5030E** (gross + employer PF + ESI) | **Salary Payable 9031** (net pay) |
| | **PF Payable 9017** (employee + employer PF) |
| | **ESI Payable 9018** |
| **Professional Tax** netted in net pay | **PT Payable 9019** |
| | **TDS Payable 9015** (salary TDS / 24Q) |

Net pay = gross − employeePf − esi(employee) − pt − tds. The form computes and shows the balancing net before posting; service re-validates `Σdr == Σcr`. Subsequent **salary payment** and **statutory deposits** are ordinary Payment vouchers (`Dr Salary/PF/ESI/PT/TDS Payable / Cr Bank`) via the existing manual voucher screens — no new code.

---

## 4D — Depreciation JV (no migration)

Thinnest slice: a pre-templated journal. Endpoint `POST /api/accounting/vouchers/depreciation` takes `period`, and one or more `{depreciationLedger?, amount, narration}` lines (defaults to 5070E):

| Dr | Cr |
|---|---|
| **Depreciation 5070E** (amount) | **Accumulated Depreciation 1020** (amount) |

VoucherType `JOURNAL`, `sourceDocType = DEPRECIATION`. When the Fixed-Asset register lands in a later phase, this endpoint becomes the posting target of the depreciation **run**; the GL contract is unchanged.

---

## 4E — UI + reports

All under `ui/src/components/accounting/`; services in `ui/src/services/accounting/`.

- **TDS master** page — `TdsSection` CRUD (admin).
- **Vendor payment dialog** — when the vendor has a PAN, show a TDS section dropdown; on select, compute `tdsAmount = round(amount × rate)`, display **Net payable = amount − TDS**, and a threshold hint. Reuses the existing record-payment dialog on the vendor-invoice page.
- **TDS register / deductee summary** — by FY + quarter; section/party-wise; deducted-vs-deposited status; Excel.
- **Challan tracker** — list pending `DEDUCTED` entries, create challan (deposits + posts), view challans; **26Q Excel** download.
- **Payroll JV** + **Depreciation JV** forms under Vouchers — structured entry that previews the balanced lines before posting.
- **Nav** in `config/modules.js`: new **TDS** section (Master, Register, Challans, 26Q); Payroll & Depreciation entries under **Vouchers**.

---

## Workstream order

`4A (TdsSection + VendorPayment fields + TdsEntry + posting branch)` → `4B (challan + TAN + 26Q export)` → `4C payroll JV` → `4D depreciation JV` → `4E UI`. 4A is the spine; 4C/4D are small and independent (can land any time after the manual-voucher engine, which already exists).

## Tests (mirror prior phases)
- `VendorPaymentPostingListenerTest` +cases: TDS branch (3-line balanced; net bank = amount − tds; tds=0 unchanged).
- `TdsEntryService` / challan: deduction creates entry `DEDUCTED`; challan flips to `DEPOSITED` + posts `Dr 9015 / Cr Bank`; idempotent.
- Payroll JV balances and posts the standard 5-credit entry; depreciation JV posts `Dr 5070E / Cr 1020`.
- 26Q export produces deductee-wise rows for the quarter.

## Deferred / known gaps (not bugs)
- TCS 206C(1H) (decision 2) — separate add-on with per-buyer threshold tracking + TCS Payable ledger.
- TDS at invoice booking / earlier-of-credit-or-payment — would need VendorInvoice TDS fields; payment-basis chosen.
- Threshold auto-enforcement (single/annual) — advisory hint only in v1.
- FVU/NSDL `.txt`, Form 16A PDF, 24Q salary detail, lower-deduction (197) — Phase 6.
- Full payroll engine and Fixed-Asset register — own modules; the JVs here are the GL contract they will post into.
