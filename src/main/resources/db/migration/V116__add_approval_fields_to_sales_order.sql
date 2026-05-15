-- V116: Add approval workflow fields to salesorder
-- Mirrors the PurchaseOrder approval model (approvalStatus, approvedBy, approvedDate, etc.)

ALTER TABLE salesorder ADD COLUMN IF NOT EXISTS approvalStatus    VARCHAR(30) DEFAULT 'DRAFT';
ALTER TABLE salesorder ADD COLUMN IF NOT EXISTS approvedBy        VARCHAR(100);
ALTER TABLE salesorder ADD COLUMN IF NOT EXISTS approvedDate      TIMESTAMP;
ALTER TABLE salesorder ADD COLUMN IF NOT EXISTS rejectionReason   VARCHAR(500);
ALTER TABLE salesorder ADD COLUMN IF NOT EXISTS sentToCustomerAt  TIMESTAMP;
ALTER TABLE salesorder ADD COLUMN IF NOT EXISTS sentToCustomerEmail VARCHAR(200);
ALTER TABLE salesorder ADD COLUMN IF NOT EXISTS revisionNo        INTEGER DEFAULT 0;
ALTER TABLE salesorder ADD COLUMN IF NOT EXISTS parentSoId        BIGINT;

-- Add CHECK constraint for approvalStatus values
ALTER TABLE salesorder DROP CONSTRAINT IF EXISTS salesorder_approvalstatus_check;
ALTER TABLE salesorder ADD CONSTRAINT salesorder_approvalstatus_check
    CHECK (approvalStatus IN ('DRAFT','PENDING_APPROVAL','APPROVED','REJECTED'));
