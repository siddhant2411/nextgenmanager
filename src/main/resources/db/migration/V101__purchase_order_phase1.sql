-- ─── Phase 1: Purchase Order Foundation ───────────────────────────────────────
-- Note: PostgreSQL folds unquoted identifiers to lowercase.
-- Existing tables: purchaseorder, purchaseorderitem (all lowercase).

-- 1. stateCode on company_details
ALTER TABLE company_details
    ADD COLUMN IF NOT EXISTS statecode VARCHAR(2);

-- 2. stateCode on contact
ALTER TABLE contact
    ADD COLUMN IF NOT EXISTS statecode VARCHAR(2);

-- 3. Drop old status CHECK constraint so we can add DRAFT and RECEIVED values
ALTER TABLE purchaseorder
    DROP CONSTRAINT IF EXISTS purchaseorder_status_check;

-- 4. Extend purchaseorder table
ALTER TABLE purchaseorder
    ADD COLUMN IF NOT EXISTS potype              VARCHAR(20)     NOT NULL DEFAULT 'STANDARD',
    ADD COLUMN IF NOT EXISTS gsttreatment        VARCHAR(30),
    ADD COLUMN IF NOT EXISTS placeofsupply       VARCHAR(2),
    ADD COLUMN IF NOT EXISTS vendorbillingaddressid INTEGER,
    ADD COLUMN IF NOT EXISTS shiptoaddressid     INTEGER,
    ADD COLUMN IF NOT EXISTS currency            VARCHAR(3)      NOT NULL DEFAULT 'INR',
    ADD COLUMN IF NOT EXISTS exchangerate        NUMERIC(18,6)   NOT NULL DEFAULT 1,
    ADD COLUMN IF NOT EXISTS paymentterms        VARCHAR(100),
    ADD COLUMN IF NOT EXISTS creditdays          INTEGER,
    ADD COLUMN IF NOT EXISTS subtotal            NUMERIC(14,2)   NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS totaldiscount       NUMERIC(14,2)   NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS taxablevalue        NUMERIC(14,2)   NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS cgstamount          NUMERIC(14,2)   NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS sgstamount          NUMERIC(14,2)   NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS igstamount          NUMERIC(14,2)   NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS cessamount          NUMERIC(14,2)   NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS roundoff            NUMERIC(6,2)    NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS grandtotal          NUMERIC(14,2)   NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS approvalstatus      VARCHAR(20)     NOT NULL DEFAULT 'DRAFT',
    ADD COLUMN IF NOT EXISTS approvedby          VARCHAR(100),
    ADD COLUMN IF NOT EXISTS approveddate        TIMESTAMP,
    ADD COLUMN IF NOT EXISTS rejectionreason     TEXT,
    ADD COLUMN IF NOT EXISTS version             INTEGER         NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS parentpoid          BIGINT,
    ADD COLUMN IF NOT EXISTS revisionno          INTEGER         NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS termsandconditions  TEXT,
    ADD COLUMN IF NOT EXISTS internalnotes       TEXT;

-- Backfill approvalStatus for existing rows
UPDATE purchaseorder
   SET approvalstatus = 'APPROVED'
 WHERE status IN ('COMPLETED', 'PARTIALLY_RECEIVED', 'SENT', 'CREATED');

-- Migrate status: CREATED → DRAFT for any truly new/unprocessed POs
UPDATE purchaseorder
   SET status = 'DRAFT'
 WHERE status = 'CREATED';

-- FK constraints for address snapshots
ALTER TABLE purchaseorder
    ADD CONSTRAINT fk_po_vendor_billing_addr
        FOREIGN KEY (vendorbillingaddressid) REFERENCES contact_address(id),
    ADD CONSTRAINT fk_po_ship_to_addr
        FOREIGN KEY (shiptoaddressid) REFERENCES contact_address(id);

-- Indices on purchaseorder
CREATE INDEX IF NOT EXISTS idx_po_status    ON purchaseorder(status);
CREATE INDEX IF NOT EXISTS idx_po_approval  ON purchaseorder(approvalstatus);
CREATE INDEX IF NOT EXISTS idx_po_orderdate ON purchaseorder(orderdate);

-- 5. Extend purchaseorderitem table
ALTER TABLE purchaseorderitem
    ADD COLUMN IF NOT EXISTS linenumber      INTEGER         NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS description     VARCHAR(500),
    ADD COLUMN IF NOT EXISTS hsncode         VARCHAR(10),
    ADD COLUMN IF NOT EXISTS uom             VARCHAR(20),
    ADD COLUMN IF NOT EXISTS discountpct     NUMERIC(5,2)    NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS discountamount  NUMERIC(14,2)   NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS taxablevalue    NUMERIC(14,2)   NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS gstratepct      NUMERIC(5,2)    NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS cgstamount      NUMERIC(14,2)   NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS sgstamount      NUMERIC(14,2)   NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS igstamount      NUMERIC(14,2)   NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS cessamount      NUMERIC(14,2)   NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS linetotal       NUMERIC(14,2)   NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS requiredbydate  DATE,
    ADD COLUMN IF NOT EXISTS salesorderitemid BIGINT;

-- 6. Approval rule table
CREATE TABLE IF NOT EXISTS purchaseorderapprovalrule (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    minamount    NUMERIC(14,2)   NOT NULL,
    maxamount    NUMERIC(14,2),
    requiredrole VARCHAR(50)     NOT NULL,
    active       BOOLEAN         NOT NULL DEFAULT TRUE
);

-- Seed: every PO requires ROLE_ADMIN approval
INSERT INTO purchaseorderapprovalrule (minamount, maxamount, requiredrole, active)
VALUES (0, NULL, 'ROLE_ADMIN', TRUE);
