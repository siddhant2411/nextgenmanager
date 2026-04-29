-- V88: Work Order Rejection & Yield Tracking
-- Adds rejection/disposition workflow for Indian manufacturer IATF/ISO 9001 compliance.
-- Tracks rejected units (pending disposition) separate from scrap (permanent loss).

-- ─── 1. Extend WorkOrderOperation with rejection fields ───────────────────────
ALTER TABLE WorkOrderOperation
    ADD COLUMN IF NOT EXISTS rejectedQuantity    DECIMAL(15, 5) NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS rejectionReasonCode VARCHAR(50),
    ADD COLUMN IF NOT EXISTS scrapReasonCode     VARCHAR(50);

-- ─── 2. Rejection Reason Code master table ────────────────────────────────────
CREATE TABLE IF NOT EXISTS rejectionReasonCode (
    id          BIGSERIAL    PRIMARY KEY,
    code        VARCHAR(50)  UNIQUE NOT NULL,
    description VARCHAR(255),
    category    VARCHAR(20)  NOT NULL DEFAULT 'BOTH',
    isActive    BOOLEAN      NOT NULL DEFAULT TRUE,
    creationDate TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Seed standard reason codes
INSERT INTO rejectionReasonCode (code, description, category) VALUES
    ('DIM_OUT_TOL',  'Dimension Out of Tolerance',  'BOTH'),
    ('SUR_DEFECT',   'Surface Defect',              'BOTH'),
    ('MAT_DEFECT',   'Material Defect (Incoming)',  'BOTH'),
    ('SETUP_ERROR',  'Setup Error',                 'BOTH'),
    ('OP_ERROR',     'Operator Error',              'BOTH'),
    ('MACH_FAIL',    'Machine Malfunction',         'BOTH'),
    ('WRONG_REV',    'Wrong Revision Used',         'REJECTION'),
    ('CUST_CHANGE',  'Customer Drawing Change',     'REJECTION')
ON CONFLICT (code) DO NOTHING;

-- ─── 3. Rejection Entry table (pending-disposition tracker) ───────────────────
CREATE TABLE IF NOT EXISTS rejectionEntry (
    id                  BIGSERIAL    PRIMARY KEY,
    workOrderId         INTEGER      NOT NULL REFERENCES workOrder(id),
    operationId         BIGINT       NOT NULL REFERENCES WorkOrderOperation(id),
    rejectedQuantity    DECIMAL(15,5) NOT NULL,
    dispositionStatus   VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    dispositionReason   VARCHAR(500),
    childWorkOrderId    INTEGER      REFERENCES workOrder(id),
    createdAt           TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    disposedAt          TIMESTAMP,
    createdBy           VARCHAR(100),
    disposedBy          VARCHAR(100)
);

CREATE INDEX IF NOT EXISTS idx_rejectionEntry_workOrder   ON rejectionEntry (workOrderId);
CREATE INDEX IF NOT EXISTS idx_rejectionEntry_disposition ON rejectionEntry (workOrderId, dispositionStatus);
