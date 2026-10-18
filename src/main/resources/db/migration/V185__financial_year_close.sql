-- V185: year-end close. A financial year records the CLOSING voucher that swept its income and
-- expenses into Retained Earnings, who closed it, and — if it was reopened — who, when and why.
-- The closing voucher is never deleted on reopen; it is reversed, and closingVoucher_id is cleared
-- so the year can be closed again.

ALTER TABLE financialyear ADD COLUMN IF NOT EXISTS closingVoucher_id BIGINT REFERENCES voucher(id);
ALTER TABLE financialyear ADD COLUMN IF NOT EXISTS closedAt     TIMESTAMP;
ALTER TABLE financialyear ADD COLUMN IF NOT EXISTS closedBy     VARCHAR(100);
ALTER TABLE financialyear ADD COLUMN IF NOT EXISTS reopenedAt   TIMESTAMP;
ALTER TABLE financialyear ADD COLUMN IF NOT EXISTS reopenedBy   VARCHAR(100);
ALTER TABLE financialyear ADD COLUMN IF NOT EXISTS reopenReason VARCHAR(500);
