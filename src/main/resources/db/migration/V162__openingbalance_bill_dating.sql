-- Per-bill dating on opening balances.
--
-- A mid-year cutover carries open invoices that are often years old. Ageing and the MSME 45-day
-- clock must bucket them by their REAL date, but the opening entry cannot be back-dated: the
-- posting service rejects any voucher whose date has no open AccountingPeriod, and periods for
-- 2023 will never exist.
--
-- So the GL keeps ONE balanced OPENING voucher dated at the cutover, and the dating lives here:
-- one openingbalance row per open bill instead of one per ledger. The rows for a ledger sum to
-- that ledger's voucher line by construction -- the import refuses to load otherwise.

ALTER TABLE openingbalance ADD COLUMN billReference VARCHAR(100);
ALTER TABLE openingbalance ADD COLUMN billDate      DATE;
ALTER TABLE openingbalance ADD COLUMN dueDate       DATE;

COMMENT ON COLUMN openingbalance.billReference IS
    'Invoice / bill number as it appears on the source document. Null for a lump ledger balance.';
COMMENT ON COLUMN openingbalance.billDate IS
    'Original document date. Drives ageing buckets and MSME 45-day, NOT the opening voucher date.';
COMMENT ON COLUMN openingbalance.dueDate IS
    'Payable-by date where the source carries one; otherwise null.';

-- Ageing reads these per party sub-ledger at report time.
CREATE INDEX idx_openingbalance_ledger_billdate
    ON openingbalance (ledgerAccount_id, billDate)
    WHERE deletedDate IS NULL AND billDate IS NOT NULL;
