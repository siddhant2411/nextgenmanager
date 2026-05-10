-- ─── Phase 1: Purchase Requisition ────────────────────────────────────────────
-- Note: PostgreSQL folds unquoted identifiers to lowercase.

CREATE TABLE IF NOT EXISTS purchaserequisition (
    id                      BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    prnumber                VARCHAR(30)     NOT NULL UNIQUE,
    requestdate             DATE,
    requiredbydate          DATE,
    requestedby             VARCHAR(100),
    department              VARCHAR(100),
    costcenter              VARCHAR(100),
    priority                VARCHAR(20)     NOT NULL DEFAULT 'NORMAL',
    status                  VARCHAR(20)     NOT NULL DEFAULT 'DRAFT',
    approvalstatus          VARCHAR(20)     NOT NULL DEFAULT 'DRAFT',
    source                  VARCHAR(20)     NOT NULL DEFAULT 'MANUAL',
    sourcerefid             BIGINT,
    sourcerefnumber         VARCHAR(100),
    totalestimatedamount    NUMERIC(14,2)   NOT NULL DEFAULT 0,
    approvedby              VARCHAR(100),
    approveddate            TIMESTAMP,
    rejectionreason         TEXT,
    notes                   TEXT,
    version                 INTEGER         NOT NULL DEFAULT 0,
    createddate             TIMESTAMP,
    updateddate             TIMESTAMP,
    deleteddate             TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_pr_status      ON purchaserequisition(status);
CREATE INDEX IF NOT EXISTS idx_pr_approval    ON purchaserequisition(approvalstatus);
CREATE INDEX IF NOT EXISTS idx_pr_source      ON purchaserequisition(source);
CREATE INDEX IF NOT EXISTS idx_pr_requestdate ON purchaserequisition(requestdate);

CREATE TABLE IF NOT EXISTS purchaserequisitionitem (
    id                      BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    requisition_id          BIGINT          NOT NULL REFERENCES purchaserequisition(id),
    linenumber              INTEGER         NOT NULL DEFAULT 0,
    item_inventoryitemid    INTEGER         REFERENCES inventoryitem(inventoryitemid),
    description             VARCHAR(500),
    uom                     VARCHAR(20),
    quantity                DOUBLE PRECISION NOT NULL DEFAULT 0,
    requiredbydate          DATE,
    suggested_vendor_id     INTEGER         REFERENCES contact(id),
    estimatedunitprice      NUMERIC(14,4)   DEFAULT 0,
    estimatedamount         NUMERIC(14,2)   DEFAULT 0,
    linestatus              VARCHAR(20)     NOT NULL DEFAULT 'PENDING',
    workordermaterialid     BIGINT,
    purchaseorderid         BIGINT,
    notes                   VARCHAR(500)
);

CREATE INDEX IF NOT EXISTS idx_pri_requisition ON purchaserequisitionitem(requisition_id);
CREATE INDEX IF NOT EXISTS idx_pri_item        ON purchaserequisitionitem(item_inventoryitemid);
CREATE INDEX IF NOT EXISTS idx_pri_linestatus  ON purchaserequisitionitem(linestatus);

CREATE TABLE IF NOT EXISTS purchaserequisitionapprovalrule (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    minamount    NUMERIC(14,2)   NOT NULL,
    maxamount    NUMERIC(14,2),
    requiredrole VARCHAR(50)     NOT NULL,
    active       BOOLEAN         NOT NULL DEFAULT TRUE
);

-- Seed: every PR requires ROLE_ADMIN approval
INSERT INTO purchaserequisitionapprovalrule (minamount, maxamount, requiredrole, active)
VALUES (0, NULL, 'ROLE_ADMIN', TRUE);
