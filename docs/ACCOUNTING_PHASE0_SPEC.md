# Accounting Module — Phase 0 Implementation Spec (Foundations)

*Companion to [ACCOUNTING_MODULE_PLAN.md](ACCOUNTING_MODULE_PLAN.md). Planned 2026-06-10.*

> **Goal of Phase 0:** A working, self-contained double-entry ledger. You can create manual vouchers, the system guarantees they balance, opening balances can be imported mid-year, and Trial Balance / Day Book / Ledger render and tie to zero. No source-document automation yet (that's Phase 1) — this phase proves the accounting core is correct before anything auto-posts into it.

---

## Exit Criteria (Phase 0 is "done" when all true)

1. Admin can build a Chart of Accounts (groups + ledgers); a standard Indian COA is pre-seeded.
2. A Financial Year + its 12 monthly periods can be created; periods can be OPEN/LOCKED/CLOSED.
3. A user can post a **manual Journal / Receipt / Payment / Contra** voucher. The system **rejects any unbalanced voucher** (Σ Dr ≠ Σ Cr).
4. A posted voucher cannot be edited or deleted — only **reversed** (creates a contra voucher).
5. **Mid-year opening balances** can be imported from Excel and produce one balancing `OPENING` voucher.
6. **Trial Balance** ties to zero; **Day Book** and **Ledger** render; all three export (PDF/Excel).
7. The **Approval Framework** exists in `common` and is wired to manual vouchers (configurable; auto-approve when no rule matches).
8. Accounting roles (`ROLE_ACCOUNTS_ADMIN`, `ROLE_ACCOUNTS_USER`, `ROLE_ACCOUNTS_HEAD`) exist and guard the endpoints.

---

## Conventions locked from the codebase (follow exactly)

- **PK / ID:** new tables use `BIGSERIAL PRIMARY KEY` + entity `@GeneratedValue(strategy = GenerationType.IDENTITY)` (matches V112/V123/V124+ — the newer convention). Don't use `AUTO`.
- **Money:** `BigDecimal` / `NUMERIC(12,2)`. **Quantity:** `NUMERIC(12,3)`.
- **Enums:** stored as `VARCHAR(n)` with a `CHECK (col IN (...))` constraint; entity `@Enumerated(EnumType.STRING)`.
- **Naming:** `PhysicalNamingStrategyStandardImpl` — column = field name (PG folds to lowercase). Write migration columns in the same camelCase the entity uses.
- **Soft delete:** `deletedDate` (Date/timestamp), filter active with `…IsNull`.
- **Audit:** `@CreationTimestamp creationDate`, `@UpdateTimestamp updatedDate`.
- **Numbering:** reuse `Inventory.model.NumberSequence` (`sequenceKey` PK, `nextVal`) + `NumberSequenceRepository.findByKeyForUpdate(key)` inside `@Transactional(propagation = REQUIRES_NEW)` — copy `TaxInvoiceNumberGenerator`.
- **Role seed:** `INSERT INTO public.role (...) VALUES (nextval('public.role_seq'), 'ROLE_X', ...) ON CONFLICT (roleName) DO UPDATE …` (copy V46).
- **Security:** add `@RequiresAccountingAccess` annotation following `common.security.authorization.RequiresSalesAccess`.
- **Package root:** `com.nextgenmanager.nextgenmanager.accounting` (+ `common.approval`).

---

## Migration plan (V128 → V132)

| Migration | Creates / Seeds |
|---|---|
| `V128__create_coa.sql` | `accountgroup`, `ledgeraccount` tables + **seed standard Indian COA** |
| `V129__create_accounting_periods.sql` | `financialyear`, `accountingperiod` tables |
| `V130__create_voucher.sql` | `voucher`, `voucherline` tables (+ indexes) |
| `V131__create_approval_framework.sql` | `approvalpolicy`, `approvalrule`, `approvalrequest`, `approvalstep` |
| `V132__seed_accounting_roles_and_opening.sql` | `openingbalance` table + seed `ROLE_ACCOUNTS_ADMIN/USER/HEAD` |

> MT private fork: mirror each as `V128_1__…` etc. in the tenant-schema migration set per the existing convention.

---

## Work-streams (build order — each is independently testable)

### 0.1 — Chart of Accounts  *(no dependencies)*
**Entities**

`AccountGroup`
```
id (IDENTITY), code (unique, 20), name (120), parentGroup_id (FK self, nullable),
nature (VARCHAR 12: ASSET/LIABILITY/EQUITY/INCOME/EXPENSE),
scheduleIIIHead (120, nullable), sortOrder (int), deletedDate
```
`LedgerAccount`
```
id (IDENTITY), code (unique, 20), name (150), group_id (FK accountgroup, NOT NULL),
nature (VARCHAR 12),
openingBalance (NUMERIC 12,2 default 0), openingBalanceDrCr (VARCHAR 2: DR/CR, nullable),
isControlAccount (bool default false),
subLedgerType (VARCHAR 12: CUSTOMER/VENDOR/BANK/CASH/TAX/TDS/NONE, default NONE),
contact_id (INTEGER FK contact(id), nullable),
gstApplicable (bool default false), gstRate (NUMERIC 5,2 nullable),
isBankAccount (bool default false), isCashAccount (bool default false), isReconcilable (bool default false),
creationDate, updatedDate, deletedDate
```
**Seed COA (V128):** insert groups first (parent-less), then child groups, then ledgers. Self-reference resolved by code lookup, e.g.:
```sql
UPDATE accountgroup c SET parentGroup_id = p.id
FROM accountgroup p WHERE c.code='CURRENT_ASSETS' AND p.code='ASSETS';
```
Seed the full list in §1 of the main plan (Sundry Debtors/Creditors as control accounts, Output/Input CGST/SGST/IGST, GR/IR Clearing, Round-off, Stock heads, etc.).

**Service/Controller:** CRUD for groups & ledgers; tree endpoint `GET /api/accounting/coa/tree`; prevent delete of a ledger that has postings. `POST/PUT/DELETE` guarded by `ROLE_ACCOUNTS_ADMIN`.

---

### 0.2 — Financial Year & Periods  *(no dependencies)*
**Entities**

`FinancialYear` `{ id, label (20), startDate, endDate, status (VARCHAR 10: ACTIVE/CLOSED), openingDate (nullable — cutover), openingEntryVoucher_id (nullable), creationDate, updatedDate }`

`AccountingPeriod` `{ id, financialYear_id (FK), periodNumber (1-12), startDate, endDate, status (VARCHAR 10: OPEN/LOCKED/CLOSED) }`

**Service:** `createFinancialYear(startYear)` derives Apr–Mar from `CompanyDetails.financialYearStartMonth` and **auto-generates the 12 periods**. `lockPeriod/unlockPeriod` (unlock requires `ROLE_ACCOUNTS_HEAD`, audit-logged). `resolvePeriod(date)` → the period containing a date (used by posting).

---

### 0.3 — Voucher core + PostingService  *(depends on 0.1, 0.2, NumberSequence)*
**Entities**

`Voucher`
```
id (IDENTITY), voucherType (VARCHAR 15 CHECK), voucherNumber (unique 40),
date (DATE), narration (500),
status (VARCHAR 18: DRAFT/PENDING_APPROVAL/POSTED/REVERSED),
sourceDocType (40 nullable), sourceDocId (BIGINT nullable),
financialYear_id (FK), period_id (FK),
reversalOf_id (FK self nullable), reversedByVoucher_id (FK self nullable),
createdBy (100), creationDate, updatedDate, deletedDate
UNIQUE (sourceDocType, sourceDocId) WHERE both not null   -- idempotency (Phase 1)
```
`VoucherLine`
```
id (IDENTITY), voucher_id (FK NOT NULL), ledgerAccount_id (FK NOT NULL),
drAmount (NUMERIC 12,2 default 0), crAmount (NUMERIC 12,2 default 0),
narration (300), costCenter_id (nullable, reserved),
taxType (VARCHAR 6 nullable: CGST/SGST/IGST/CESS), hsnSac (10 nullable),
taxableValue (NUMERIC 12,2 nullable), taxRate (NUMERIC 5,2 nullable),
deletedDate
```

**`VoucherNumberGenerator`** — copy `TaxInvoiceNumberGenerator`; key = `"{TYPE}-" + fy` (e.g. `JNL-2025-26`), output `"{TYPE}/{fy}/%04d"` → `JNL/2025-26/0001`.

**`PostingService` (the heart)** — interface:
```java
public interface PostingService {
    /** Validate → assign number → persist as POSTED (or PENDING_APPROVAL if a rule matches). */
    Voucher post(VoucherDraft draft, String username);

    /** Create a reversing voucher (swaps Dr/Cr); links both via reversalOf/reversedBy. */
    Voucher reverse(Long voucherId, String reason, String username);
}
```
`VoucherDraft = { voucherType, date, narration, List<Line> }`, `Line = { ledgerAccountId, drAmount, crAmount, narration, taxType?, hsnSac?, taxableValue?, taxRate? }`.

**Validation rules enforced in `post()` (throw `UnbalancedVoucherException` / `InvalidVoucherException`):**
1. ≥ 2 lines.
2. Each line: exactly one of `drAmount`/`crAmount` > 0, the other == 0.
3. `Σ drAmount.compareTo(Σ crAmount) == 0` (scale 2).
4. Every `ledgerAccountId` exists and `deletedDate IS NULL`.
5. `resolvePeriod(date)` exists and is **OPEN** (else reject).
6. After validation: call `ApprovalEngine.evaluate("MANUAL_VOUCHER", {amount=Σdr, voucherType})`. If steps required → status `PENDING_APPROVAL` (does **not** hit Trial Balance); else `POSTED`.

**Posting is immutable:** no update/delete endpoints on a `POSTED` voucher. `reverse()` is the only correction path.

---

### 0.4 — Manual voucher endpoints  *(thin layer over PostingService)*
- `POST /api/accounting/vouchers/journal` — free-form Dr/Cr lines.
- `POST /api/accounting/vouchers/receipt` — Dr Bank/Cash, Cr party/income.
- `POST /api/accounting/vouchers/payment` — Dr party/expense, Cr Bank/Cash.
- `POST /api/accounting/vouchers/contra` — Dr Cash/Bank, Cr Bank/Cash.
- `POST /api/accounting/vouchers/{id}/reverse` — body `{reason}`.
- `GET /api/accounting/vouchers/{id}` , `GET /api/accounting/vouchers?from&to&type&status`.

Receipt/Payment/Contra are convenience wrappers that build a 2-line `VoucherDraft` and call `post()`. All write endpoints guarded by `ROLE_ACCOUNTS_USER`+; reverse by `ROLE_ACCOUNTS_HEAD`.

---

### 0.5 — Reports  *(read over voucher/voucherline)*
**Trial Balance** — `GET /api/accounting/reports/trial-balance?asOf=`
```sql
SELECT la.id, la.code, la.name, la.nature,
       COALESCE(SUM(vl.drAmount),0) AS dr, COALESCE(SUM(vl.crAmount),0) AS cr
FROM ledgeraccount la
LEFT JOIN voucherline vl ON vl.ledgeraccount_id = la.id AND vl.deletedDate IS NULL
LEFT JOIN voucher v ON v.id = vl.voucher_id
WHERE v.status = 'POSTED' AND v.deletedDate IS NULL AND v.date <= :asOf
GROUP BY la.id …
```
Net each account = opening balance ± movement; group totals by `AccountGroup`; assert `Σdr == Σcr` (tie check shown on report). Export PDF + Excel.

**Day Book** — `GET …/day-book?from&to` — posted vouchers in range with their lines, ordered by date then voucherNumber. Flag missing sequence numbers (audit).

**Ledger (account statement)** — `GET …/ledger/{accountId}?from&to` — opening balance + chronological lines with **running balance**.

---

### 0.6 — Approval Framework (`common.approval`)  *(no accounting dependency)*
**Entities** (see main plan §6 for full fields): `ApprovalPolicy`, `ApprovalRule` (conditions as JSON `text` column — parse with the existing Jackson `ObjectMapper`), `ApprovalRequest`, `ApprovalStep`.

**`ApprovalEngine`**
```java
public interface ApprovalEngine {
    /** Returns the ordered approver steps required, or empty if none (auto-approve). */
    List<RequiredStep> evaluate(String documentType, Map<String,Object> context);

    ApprovalRequest submit(String documentType, Long documentId, Map<String,Object> context, String requestedBy);
    void act(Long stepId, boolean approved, String comment, String actedBy); // advances/rejects
}
```
- Condition eval: load enabled `ApprovalPolicy` for `documentType`, iterate rules in `sortOrder`, test each `{attribute, operator, value}` against `context`. Operators: `>=,<=,=,!=,IN,NOT_IN`.
- On final step approval → publish `ApprovalCompletedEvent(documentType, documentId)` via `DomainEventPublisher`. In Phase 0 the manual-voucher flow listens and flips `PENDING_APPROVAL → POSTED`.
- Endpoints: `GET /api/approvals/inbox` (steps PENDING for caller's roles), `POST /api/approvals/steps/{id}/act`, admin CRUD for policies/rules under `/api/approval-policies`.

---

### 0.7 — Opening Balances (mid-year cutover)  *(depends on 0.1, 0.3)*
**Entity** `OpeningBalance { id, ledgerAccount_id (FK), contact_id (nullable, party sub-ledger), amount (12,2), drCr (DR/CR), openingDate, importedAt, deletedDate }`

**Flow**
1. `POST /api/accounting/opening-balances/import` — Excel upload (Apache POI), template columns: `ledgerCode, contactCode?, amount, drCr`.
2. Validate Σ DR == Σ CR across all rows (reject otherwise).
3. Persist `OpeningBalance` rows + generate one `OPENING`-type `Voucher` dated `FinancialYear.openingDate` (must balance — runs through `PostingService.post()`).
4. Set `FinancialYear.openingDate` = cutover date; `openingEntryVoucher_id` = the generated voucher.
5. Separate templates/endpoints for **open debtors** and **open creditors** (party-wise rows → posted against the Sundry Debtors/Creditors control accounts so ageing works from day 1).

---

### 0.8 — Security & Roles  *(V132 seed)*
Seed `ROLE_ACCOUNTS_ADMIN` (manage COA, periods, policies), `ROLE_ACCOUNTS_USER` (create vouchers, view reports), `ROLE_ACCOUNTS_HEAD` (approve, reverse, lock/unlock periods), `moduleName='ACCOUNTING'`. Add `@RequiresAccountingAccess` (mirror `RequiresSalesAccess`). Map to the admin user in the seed like V46 does.

---

### 0.9 — Frontend (after backend green)
React, under `ui/src` following existing service/component conventions:
- `accountingService.js` (axios calls).
- Masters: **Chart of Accounts** (tree), **Financial Year** setup, **Opening Balances** import.
- Vouchers: **Journal/Receipt/Payment/Contra** entry (Tally-style Dr/Cr grid, account autocomplete, live balance indicator that blocks submit until Dr==Cr), **Approvals Inbox**.
- Reports: **Trial Balance**, **Day Book**, **Ledger** (with PDF/Excel download buttons).

---

## Testing strategy (Phase 0)

- **Unit (`PostingService`):** rejects unbalanced; rejects line with both Dr&Cr; rejects posting into LOCKED period; reverse produces mirror voucher.
- **Unit (`ApprovalEngine`):** condition matching for amount threshold, IN-list (export), no-rule → empty.
- **Integration:** seed COA → post several vouchers → assert Trial Balance ties to zero; import opening balances → assert opening voucher balances and TB still ties.
- Follow existing test layout in `src/test/java/.../common/service/`.

---

## Recommended sequencing

Backend first in dependency order **0.1 → 0.2 → 0.3 → 0.4 → 0.5**, then **0.6 (approval)**, **0.7 (opening balances)**, **0.8 (roles/security)**, finally **0.9 (UI)**. 0.1, 0.2, and 0.6 can be parallelized as they have no cross-dependencies. The critical path is **0.3 PostingService** — everything downstream depends on it being correct, so it gets the most test coverage.

---

## Open decisions for Phase 0 (recommendation in bold)

1. **Build order:** **Backend-complete (0.1–0.8) then UI (0.9)** vs. vertical slices (COA backend+UI, then vouchers backend+UI). Recommend backend-first so the posting core is proven before any UI.
2. **Cost centers:** `costCenter_id` is on `VoucherLine` as a **reserved nullable column now, full Cost Center master deferred to a later phase** — avoids a migration later. Confirm OK.
3. **Approval rule storage:** conditions as **JSON text column** (flexible, no rule-table explosion) vs. normalized condition rows. Recommend JSON.
