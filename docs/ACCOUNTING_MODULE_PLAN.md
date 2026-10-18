# Accounting Module — End-to-End Plan (India / GST)

*Planned: 2026-06-10. Status: Architecture fully decided, Phase 0 ready to build.*

---

## Confirmed Decisions (locked — do not re-litigate)

| # | Decision | Choice | Notes |
|---|---|---|---|
| 1 | Inventory accounting | **Perpetual** | `InventoryLedger` already values every movement (FIFO/AVG/STD). Monthly GL ↔ stock reconciliation report in Phase 3. |
| 2 | Approval workflow | **Configurable rule-driven engine in `common`** | Manual vouchers need approval by default; any document type can plug in rules. Auto-approve when no rule matches (Option a). |
| 3 | Go-live timing | **Mid-year supported** | Opening balances are first-class in Phase 0 — not deferred. |
| 4 | Multi-GSTIN | **Single GSTIN now** | Multi-tenant isolation stays at schema-separation layer. Additive change later if needed. |

---

## What Already Exists (do NOT rebuild)

### Source documents with GST data
- **Sales:** `TaxInvoice` (CGST/SGST/IGST per item), `InvoiceItem`, `SalesPayment` (customer receipts), `DeliveryNote`
- **Purchase:** `VendorInvoice` (CGST/SGST/IGST/Cess), `DebitNote` (purchase returns), `PurchaseOrder`
- **GST helpers:** `GstResolver` (intra/inter-state), `GstTreatment` enum (INTRA_STATE / INTER_STATE / EXPORT_WITH_LUT / EXPORT_WITH_TAX / SEZ / RCM / COMPOSITION / UNREGISTERED), `GstType` on `Contact`
- **Inventory:** `InventoryLedger` (values every movement, referenceType/No, valuationMethod), COGS columns (V120)
- **Masters:** `Contact` (GSTIN, PAN, stateCode, GstType, MSME/URN, creditDays), `CompanyDetails` (GSTIN, PAN, CIN, bank, `financialYearStartMonth = 4`)
- **Existing approvals (migrate onto new engine later):** `SalesOrderApprovalStatus` (V116)

### What does NOT exist yet (build from scratch)
- Chart of Accounts, double-entry General Ledger, journal vouchers
- Trial Balance / P&L / Balance Sheet
- GST registers and return generation (GSTR-1 / 3B / 2B)
- TDS / TCS
- `VendorPayment` — only `SalesPayment` exists today
- `SalesCreditNote` — debit notes exist on purchase side only
- Accounting periods / financial year
- Generic configurable approval framework

### Latest Flyway migration: **V127**
New accounting migrations start at **V128**. MT-specific tenant-schema migrations use `V128_1__` sub-versioning convention.

---

## Module Structure

New Java module: `com.nextgenmanager.nextgenmanager.accounting`

```
accounting/
├── coa/          AccountGroup, LedgerAccount
├── period/       FinancialYear, AccountingPeriod, OpeningBalance
├── voucher/      Voucher, VoucherLine, VoucherType, VoucherNumberGenerator
├── posting/      PostingService, PostingRule, event listeners
├── gst/          GstRegister, Gstr1Builder, Gstr3bBuilder, Gstr2bReconciler, HsnSummary
├── tds/          TdsSection, TdsEntry, TdsChallan
├── bank/         BankAccount, BankStatementLine, BankReconciliation
├── reports/      TrialBalance, ProfitLoss, BalanceSheet, CashFlow, AgeingReport
└── close/        YearEndClosing, PeriodLock

common/
└── approval/     ApprovalPolicy, ApprovalRule, ApprovalRequest, ApprovalStep, ApprovalEngine
```

Follows project pattern: `model → repository → service (interface+impl) → controller → dto → mapper`
Rules: column names = exact field names (`PhysicalNamingStrategyStandardImpl`), soft delete via `deletedDate`, `BigDecimal` for all money.

---

## 1. Chart of Accounts (COA)

### `AccountGroup` — Schedule III aligned, self-referencing tree
```
id, code, name, parentGroup_id (self-ref), nature (ASSET/LIABILITY/INCOME/EXPENSE/EQUITY),
scheduleIIIHead, sortOrder, deletedDate
```

### `LedgerAccount` — the postable leaf
```
id, code, name, group_id, nature,
openingBalance, openingBalanceDrCr (DR/CR),
isControlAccount,           -- e.g. Sundry Debtors is a control account
subLedgerType (CUSTOMER/VENDOR/BANK/CASH/TAX/TDS/NONE),
contact_id (FK → Contact),  -- party sub-ledgers: one row per customer / vendor
gstApplicable, gstRate,
isBankAccount, isCashAccount, isReconcilable,
deletedDate
```

**Party sub-ledgers:** Every Contact that transacts gets its own `LedgerAccount` under Sundry Debtors (customers) or Sundry Creditors (vendors). AR/AP ageing derives from these sub-ledgers.

### Seeded Indian COA (V128 migration)

**Assets**
- Fixed Assets (Plant & Machinery, Furniture & Fixtures, Vehicles)
- Accumulated Depreciation (contra asset)
- Stock-in-Hand: Raw Material, WIP, Finished Goods, Packing Material
- Sundry Debtors *(control account)*
- Cash-in-Hand, Bank Accounts
- Loans & Advances Receivable
- TDS Receivable, Advance Tax Paid
- Input CGST, Input SGST, Input IGST, Input Cess
- GST Paid under RCM
- GR/IR Clearing *(perpetual inventory bridge account)*

**Liabilities & Equity**
- Share Capital, Retained Earnings / P&L Reserve
- Secured Loans, Unsecured Loans
- Sundry Creditors *(control account)*
- Output CGST, Output SGST, Output IGST, Output Cess
- RCM Payable, GST Payable (net)
- TDS Payable (194C, 194J, 194Q, 206C…)
- PF Payable, ESI Payable, PT Payable
- Salary Payable
- Advance from Customers

**Income**
- Sales — Domestic, Sales — Export
- Other Income, Interest Received, Discount Received
- Round-off Income

**Expenses**
- Purchases — Raw Material, Purchases — Trading Goods
- COGS / Cost of Production
- Direct: Freight Inward, Job-Work Charges, Packing
- Manufacturing Overhead (Power & Fuel, Consumables)
- Salaries & Wages, Labour Charges
- Admin: Rent, Printing, Telephone, Office Expenses
- Financial: Bank Charges, Interest Paid
- Depreciation
- Discount Allowed
- Round-off Expense

---

## 2. Financial Year & Periods

### `FinancialYear`
```
id, label ("FY 2025-26"), startDate, endDate,
status (ACTIVE/CLOSED),
openingEntryVoucherId (FK — the balancing opening voucher)
```
Derived from `CompanyDetails.financialYearStartMonth = 4` (April–March).

### `AccountingPeriod`
```
id, financialYear_id, periodNumber (1–12), startDate, endDate,
status (OPEN / LOCKED / CLOSED)
```
**Posting validation:** `PostingService` rejects any voucher dated in a LOCKED or CLOSED period.

---

## 3. Voucher System (double-entry core)

### `Voucher` (header)
```
id, voucherType, voucherNumber,     -- sequential per type per FY, no gaps
date, narration,
status (DRAFT / PENDING_APPROVAL / POSTED / REVERSED),
sourceDocType, sourceDocId,         -- idempotency key: (type, id) — never double-post
financialYear_id, period_id,
reversedByVoucher_id, reversalOf_id,
createdBy, createdDate, updatedDate
```

### `VoucherLine`
```
id, voucher_id, ledgerAccount_id,
drAmount (BigDecimal), crAmount (BigDecimal),  -- one of these is always 0
narration, costCenter_id,
taxType (CGST/SGST/IGST/CESS), hsnSac, taxableValue, taxRate,  -- GST register fields
deletedDate
```

**Hard invariant in `PostingService`:** `Σ drAmount == Σ crAmount` across all lines. Throws `UnbalancedVoucherException` otherwise. System will never post an unbalanced entry.

### `VoucherType` enum
`JOURNAL, SALES, PURCHASE, RECEIPT, PAYMENT, CONTRA, CREDIT_NOTE, DEBIT_NOTE, OPENING, CLOSING, DEPRECIATION, PAYROLL`

### Number format
`{TYPE}/{FY_SHORT}/{SEQUENCE}` — e.g. `REC/2526/00045`, `JNL/2526/00012`
Gap detection runs in Trial Balance and Day Book reports (audit flag).

---

## 4. Sub-Ledger Posting Engine

`PostingService` consumes domain events published by existing modules via `DomainEventPublisher` (same Spring Events pattern as BOM audit listeners). **Zero coupling back** — accounting listens; source modules know nothing about accounting.

### Complete posting table (the CA contract)

| Source event | Debit | Credit |
|---|---|---|
| **Tax Invoice** → ISSUED | Customer sub-ledger | Sales, Output CGST/SGST or IGST, Round-off |
| **SalesPayment** recorded | Bank / Cash | Customer sub-ledger (or Advance-from-Customer) |
| **SalesCreditNote** issued *(new)* | Sales, Output GST | Customer sub-ledger |
| **VendorInvoice** → APPROVED | Purchase / Inventory, Input CGST/SGST/IGST | Vendor sub-ledger |
| **RCM vendor bill** | Input GST (RCM), Purchase | Vendor sub-ledger + RCM Payable |
| **VendorPayment** *(new entity)* | Vendor sub-ledger | Bank / Cash; TDS portion → TDS Payable |
| **DebitNote** (purchase return) | Vendor sub-ledger | Purchase, Input GST |
| **GRN** received (perpetual) | Inventory — Raw Material | GR/IR Clearing |
| **GRN** matched to Vendor Invoice | GR/IR Clearing | Vendor sub-ledger |
| **Stock adjustment** | Inventory ⇄ Stock Adjustment Expense | (per `InventoryLedger.valuationMethod`) |
| **WO material issue** | WIP | Raw Material |
| **WO finished goods completion** | Finished Goods | WIP + Overhead absorbed |
| **Sale dispatch** (COGS) | COGS | Finished Goods / Inventory |
| **Payroll** processed | Salaries & Wages | Salary Payable, TDS Payable, PF/ESI Payable |
| **Depreciation** run | Depreciation Expense | Accumulated Depreciation |
| **Job-work challan** | Job-work Expense, Input GST | Vendor sub-ledger |

GST ledger selection: `GstResolver.deriveGstTreatment()` → intra-state → CGST+SGST; inter-state → IGST.
Round-off: auto-computed on every sales/purchase posting to balance the paise.

**Edit before lock → reverse + repost:**
Old voucher → `REVERSED` status + `reversedByVoucher_id` set → new voucher posted. Full audit trail preserved.

---

## 5. Gaps in Existing Modules (build in Phase 1)

### `VendorPayment` (symmetric mirror of `SalesPayment`)
```
id, vendorInvoice_id, vendor_id (FK → Contact),
paymentDate, amount (BigDecimal), paymentMode (PaymentMode enum),
referenceNumber, bankAccount, notes,
tdsAmount, tdsSection_id,
createdBy, createdDate, deletedDate
```
API: `POST /api/vendor-invoices/{invoiceId}/payments`

### `SalesCreditNote`
Document for sales returns. Needed for:
- GSTR-1 credit-note section (CDN table)
- GL reversal posting (Dr Sales, Dr Output GST → Cr Customer)

---

## 6. Configurable Approval Framework (`common.approval`)

Lives in `common` — any document type plugs in. Replacing the ad-hoc `SalesOrderApprovalStatus` over time.

### `ApprovalPolicy`
```
id, documentType (MANUAL_VOUCHER/SALES_ORDER/PURCHASE_ORDER/VENDOR_INVOICE/…),
enabled (boolean), companyId, createdDate
```

### `ApprovalRule`
```
id, policy_id, sortOrder,
conditions (JSON — array of {attribute, operator, value}),
approverRole (FK → Role), level (int — for multi-step escalation),
description
```
Condition attributes: `amount`, `orderType`, `gstTreatment`, `partyCategory`, `voucherType`, `paymentMode`
Operators: `>=`, `<=`, `=`, `!=`, `IN`, `NOT_IN`

### `ApprovalRequest`
```
id, documentType, documentId,
status (PENDING / APPROVED / REJECTED / CANCELLED),
requestedBy, requestedAt
```

### `ApprovalStep`
```
id, request_id, level (int), approverRole,
status (PENDING / APPROVED / REJECTED),
actedBy, actedAt, comment
```

### Engine flow
1. Document "submits" → `ApprovalEngine.evaluate(documentType, context)` called
2. Engine finds matching enabled rules → builds ordered step chain
3. **Steps = 0** → auto-approve → document proceeds / posts immediately *(Option a default)*
4. **Steps > 0** → document → `PENDING_APPROVAL`, steps created, approvers notified in inbox
5. Each level approves in sequence; rejection → back to maker with comment, full trail logged
6. Final approval → fires domain event → GL posting happens

### Config examples (these are DB rows, not code changes)

| Use case | documentType | Condition | Approver role | Level |
|---|---|---|---|---|
| Watch all purchases | PURCHASE_ORDER | *(none — always)* | ROLE_PURCHASE_HEAD | 1 |
| Watch all sales | SALES_ORDER | *(none)* | ROLE_SALES_HEAD | 1 |
| Export orders only | SALES_ORDER | `gstTreatment IN (EXPORT_WITH_LUT, EXPORT_WITH_TAX, SEZ)` | ROLE_EXPORT_MANAGER | 1 |
| Orders ≥ ₹50,000 | SALES_ORDER | `amount >= 50000` | ROLE_FINANCE_MANAGER | 1 |
| Orders ≥ ₹5,00,000 | SALES_ORDER | `amount >= 500000` | ROLE_FINANCE_MANAGER then ROLE_DIRECTOR | 1 → 2 |
| Manual vouchers | MANUAL_VOUCHER | *(none — always)* | ROLE_ACCOUNTS_HEAD | 1 |

---

## 7. GST Engine

All GST data already exists in invoice items. This layer adds registers and return builders.

### GST Registers (queryable by period)
- **Outward supplies:** from `TaxInvoice` / `SalesCreditNote` — GSTIN, place of supply, invoiceNo/date, HSN/SAC, taxable value, rate, CGST/SGST/IGST split
- **Inward supplies + ITC:** from `VendorInvoice` — same fields + ITC eligible flag
- **RCM register:** separate from regular ITC
- **Ineligible ITC:** blocked credits per Section 17(5)

### GSTR-1 (outward — monthly)
Tables: B2B, B2C-Large (> ₹2.5L), B2C-Small, Exports (EXPWP/EXPWOP), Credit/Debit Notes, HSN Summary, Document Series
Export: **JSON (GSTN offline-tool schema)** + Excel (Apache POI)

### GSTR-3B (summary — monthly)
Auto-computed: outward liability, eligible ITC, RCM, net payable
Reconciled to GL Output/Input tax ledgers — 3B must match ledger (CA check)
Export: JSON + Excel

### GSTR-2B Reconciliation (Phase 6)
Import GSTN 2B JSON → match against `VendorInvoice` by (GSTIN + invoiceNumber + value)
Statuses: MATCHED / AMOUNT_MISMATCH / IN_BOOKS_NOT_IN_2B / IN_2B_NOT_IN_BOOKS

### HSN/SAC Summary
Mandatory for GSTR-1 from Aug 2023 (> 5 Cr turnover mandatory, below voluntary)

### E-invoice / E-way Bill (Phase 6)
Model fields in IRP JSON schema ready; live IRP/NIC API deferred (needs GSP empanelment)

---

## 8. TDS / TCS

### `TdsSection` master
```
id, section ("194C"), description, rate (BigDecimal),
panMissingRate, thresholdAmount, applicableTo (VENDOR_PAYMENT/SALES/BOTH)
```

### `TdsEntry`
```
id, section_id, contact_id, amount, tdsAmount,
sourceDocType, sourceDocId, challan_id,
financialYear, period, status (DEDUCTED / DEPOSITED)
```

### Flow
- `VendorPayment` → if vendor has PAN + applicable section → deduct TDS → Dr Vendor, Cr Bank (net), Cr TDS Payable
- TCS 206C(1H) on sales when aggregate crosses threshold
- `TdsChallan`: quarterly deposit tracking; Form 26Q/27Q data export; Form 16A inputs

---

## 9. Financial Reports

| Report | Source | Output |
|---|---|---|
| Day Book | All posted vouchers by date | PDF + Excel |
| Ledger | VoucherLine per account, running balance | PDF + Excel |
| **Trial Balance** | Σ Dr / Σ Cr per LedgerAccount — must net to zero | PDF + Excel |
| **P&L** | Income − Expense ledgers, Schedule III | PDF |
| **Balance Sheet** | Assets vs Liabilities+Equity, Schedule III | PDF |
| Cash Flow | Indirect method | PDF |
| **Debtors Ageing** | Open items per customer sub-ledger, 0–30/31–60/61–90/90+ | Excel |
| **Creditors Ageing** | Open items per vendor sub-ledger | Excel |
| MSME Dues | `Contact.msmeRegistered=true`, bills > 45 days unpaid | Excel |
| Bank Reconciliation | BankStatementLine vs GL Cash/Bank | PDF |
| GST Liability vs ITC | Output tax vs Input tax, net payable | Excel |
| Stock vs GL | `InventoryLedger` total vs Stock-in-Hand control account | Excel |

PDF: OpenHTMLtoPDF + Thymeleaf (same stack as invoices). Excel: Apache POI (same as inventory export).

---

## 10. Mid-Year Cutover (Opening Balances)

Because go-live is mid-year, opening balances are first-class Phase 0 features.

### What to capture at cutover date

1. **Trial balance** as of cutover date → imported as one balancing `OPENING` type voucher (Σ Dr = Σ Cr)
2. **Open debtors:** each unpaid/part-paid customer invoice with original issue date (ageing correct from day 1)
3. **Open creditors:** each unpaid vendor invoice with original date
4. **Opening stock:** qty + value per item (must tie to `InventoryLedger`)
5. **GST carry-forward:** ITC balance per head (CGST/SGST/IGST) from last filed return
6. **TDS balances:** deducted but not yet deposited

### `OpeningBalance` entity
```
id, ledgerAccount_id, contact_id (for party sub-ledgers),
amount (BigDecimal), drCr (DR/CR), openingDate, importedAt
```

### Mechanism
- Excel import template per category (Apache POI)
- System generates single `OPENING` voucher from import — must balance
- `FinancialYear.openingDate` stores the cutover date
- Transactions dated after `openingDate` → normal GL posting
- Transactions dated before `openingDate` → read-only historical records, no GL posting

---

## 11. Period Lock & Year-End Close

### Period lock
- Month locked after filing GSTR-1/3B for that period
- Locked period: no new postings, no edits
- Late entry: ROLE_ACCOUNTS_HEAD can unlock → post → relock (every unlock/relock audit-logged)

### Year-end close sequence
1. Run depreciation entries for the year
2. Verify Trial Balance ties to zero
3. System generates `CLOSING` voucher: all Income/Expense balances → Retained Earnings
4. BS account balances carried forward as opening of new FY
5. P&L accounts reset to zero
6. FY status → CLOSED; new FY + 12 periods auto-created with status OPEN

---

## 12. CA Approval Checklist (non-negotiables)

These are built into the foundation — not optional:

- [ ] Strict double-entry: `PostingService` throws on unbalanced voucher
- [ ] Sub-ledger automation: every source document auto-generates its voucher (no re-keying by accountant)
- [ ] Immutability: posted voucher never edited/deleted — correction only by reversal (contra voucher)
- [ ] Audit trail: every change event-logged with who/when/before/after (Spring Events pattern)
- [ ] Locked periods: no back-dating into LOCKED or CLOSED periods
- [ ] Sequential voucher numbers with gap detection in reports
- [ ] Reconciliation by construction: 3B ties to GL; AR/AP sub-ledger ties to control accounts; bank ties to statement
- [ ] Segregation of duties: configurable maker-checker via `ApprovalFramework`
- [ ] MSME dues monitoring (45-day breach report from `Contact.msmeRegistered`)
- [ ] FY close: P&L → Retained Earnings; BS balances carry forward correctly

---

## 13. Frontend (`ui/src`)

New **Accounting** top-level menu:

```
Accounting/
├── Masters/
│   ├── Chart of Accounts (tree: groups + ledgers, add/edit)
│   ├── Financial Year Setup (years + period status grid)
│   └── Opening Balances (Excel import + manual entry)
├── Vouchers/
│   ├── Journal Voucher
│   ├── Receipt Voucher
│   ├── Payment Voucher
│   ├── Contra Voucher
│   └── Approvals Inbox (pending for logged-in user's role)
├── Reports/
│   ├── Day Book
│   ├── Ledger (account-wise with date range)
│   ├── Trial Balance
│   ├── Profit & Loss
│   ├── Balance Sheet
│   ├── Cash Flow
│   ├── Debtors Ageing
│   └── Creditors Ageing
├── GST/
│   ├── GSTR-1 (preview + JSON/Excel download)
│   ├── GSTR-3B (summary reconciled to GL + export)
│   ├── GSTR-2B Reconciliation (Phase 6)
│   └── HSN Summary
├── TDS/
│   ├── TDS Entries
│   ├── Challans
│   └── 26Q Export
└── Bank/
    ├── Bank Accounts
    └── Bank Reconciliation
```

**Approval Settings** (Admin menu): pick document type → configure rules → assign approver roles → enable/disable.

**Voucher entry UI:** Tally-style keyboard-driven (Dr/Cr grid, account search, amount, narration). Auto-posted vouchers are read-only with a drill-back link to the source document.

---

## 14. Phased Roadmap

| Phase | Scope | Deliverable | Migrations |
|---|---|---|---|
| **0 — Foundations** | `AccountGroup`, `LedgerAccount`, seeded Indian COA; `FinancialYear`, `AccountingPeriod`; `Voucher`/`VoucherLine`; `PostingService` (balanced-entry enforcement); manual Journal / Receipt / Payment / Contra; mid-year opening balance Excel import; Day Book, Ledger, Trial Balance; `ApprovalFramework` in `common`; `ROLE_ACCOUNTS_HEAD` | Working double-entry books; books always balance; manual vouchers with approval | V128–V132 |
| **1 — AR/AP automation** | Events from `TaxInvoice`, `SalesPayment` → auto-post; **`VendorPayment`** entity + API; **`SalesCreditNote`**; `VendorInvoice` / `DebitNote` → auto-post; Debtors/Creditors sub-ledgers; Ageing reports; Approval framework wired to MANUAL_VOUCHER | Sales & purchase cycles hit GL automatically; AR/AP dashboards live | V133–V136 |
| **2 — GST returns** | GST registers (outward/inward/RCM); **GSTR-1** JSON+Excel; **GSTR-3B** reconciled to GL; HSN summary; period lock on return filing | File-ready GST returns from the books | V137–V139 |
| **3 — Inventory & production** | Perpetual inventory posting from `InventoryLedger` events; GR/IR clearing; WIP/FG/overhead from production cost; COGS on dispatch; monthly GL ↔ stock reconciliation report | Inventory value ties to BS Stock-in-hand on any date | V140–V142 |
| **4 — TDS/TCS** | `TdsSection` master; TDS on `VendorPayment`; TCS on sales; challan tracking; 26Q/27Q export; payroll and depreciation vouchers | Statutory deductions fully in books | V143–V145 |
| **5 — Statements & year close** | Schedule III P&L + Balance Sheet; Cash Flow (indirect); Bank Reconciliation; **FY year-end close**; period lock workflow; maker-checker hardening | Audit-ready financials; clean year-end | V146–V148 |
| **6 — Advanced** | GSTR-2B reconciliation; GSTR-9 annual; E-invoice (IRN/QR — IRP JSON schema); E-way bill; MIS dashboards & ratios | Full statutory compliance + automation | V149+ |

---

## 15. Key Entity Relationships

```
FinancialYear
  └─ AccountingPeriod (12 per year, OPEN/LOCKED/CLOSED)

AccountGroup (self-referencing tree, Schedule III)
  └─ LedgerAccount
       ├─ contact_id → Contact  (party sub-ledger: one account per customer/vendor)
       └─ subLedgerType, isControlAccount

Voucher
  ├─ period_id         → AccountingPeriod
  ├─ sourceDocType     } idempotency key — prevent double-posting
  ├─ sourceDocId       }
  ├─ reversedByVoucher → Voucher (self-ref, for reversals)
  └─ VoucherLine[]
       └─ ledgerAccount_id → LedgerAccount

ApprovalPolicy (one per company + documentType)
  └─ ApprovalRule[] (conditions JSON, approverRole, level)

ApprovalRequest (one per document submission)
  └─ ApprovalStep[] (one per level: status, actedBy, comment)
```

---

## 16. Picking Up Implementation

When starting a new session on this module:

1. Read this file first.
2. Check current Flyway migrations (`src/main/resources/db/migration/`) to confirm the latest V number.
3. Identify the current Phase from the roadmap (§14).
4. Use the posting table (§4) as the contract for any new voucher type — derive Dr/Cr from there.
5. All new entities: follow `PhysicalNamingStrategyStandardImpl`, `BigDecimal` money, `deletedDate` soft-delete, `@CreationTimestamp`/`@UpdateTimestamp` audit fields.
6. New Spring Events follow the pattern in `bom/events` + `bom/listeners` + `common/events/DomainEventPublisher`.
7. New migrations: `V{next}__description.sql`; MT-specific tenant-schema migrations: `V{next}_1__description.sql`.
