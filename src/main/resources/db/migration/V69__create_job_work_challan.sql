-- Job Work Challan (GST Rule 45 / CGST Act Section 143)
-- Tracks materials dispatched to job workers and their return.

CREATE TABLE jobWorkChallan (
    id                    BIGSERIAL PRIMARY KEY,
    challanNumber         VARCHAR(30)  NOT NULL UNIQUE,
    vendorId              INTEGER      NOT NULL REFERENCES contact(id),
    workOrderId           BIGINT       REFERENCES workorder(id),
    workOrderOperationId  BIGINT       REFERENCES workorderoperation(id),
    status                VARCHAR(25)  NOT NULL DEFAULT 'DRAFT',
    dispatchDate          TIMESTAMP,
    expectedReturnDate    TIMESTAMP,
    actualReturnDate      TIMESTAMP,
    agreedRatePerUnit     NUMERIC(14,4),
    dispatchDetails       VARCHAR(200),
    remarks               TEXT,
    creationDate          TIMESTAMP    NOT NULL DEFAULT NOW(),
    updatedDate           TIMESTAMP    NOT NULL DEFAULT NOW(),
    deletedDate           TIMESTAMP
);

CREATE INDEX idx_challan_vendor    ON jobWorkChallan(vendorId);
CREATE INDEX idx_challan_workorder ON jobWorkChallan(workOrderId);
CREATE INDEX idx_challan_status    ON jobWorkChallan(status);

-- ─── Lines ───────────────────────────────────────────────────────────────────

CREATE TABLE jobWorkChallanLine (
    id                  BIGSERIAL    PRIMARY KEY,
    challanId           BIGINT       NOT NULL REFERENCES jobWorkChallan(id),
    inventoryItemId     INTEGER      REFERENCES inventoryItem(inventoryItemId),
    description         VARCHAR(200),
    hsnCode             VARCHAR(10),
    quantityDispatched  NUMERIC(15,5) NOT NULL,
    quantityReceived    NUMERIC(15,5) NOT NULL DEFAULT 0,
    quantityRejected    NUMERIC(15,5) NOT NULL DEFAULT 0,
    uom                 VARCHAR(20),
    valuePerUnit        NUMERIC(14,4),
    remarks             TEXT,
    lastReceiptDate     TIMESTAMP
);

CREATE INDEX idx_challan_line_challan ON jobWorkChallanLine(challanId);
CREATE INDEX idx_challan_line_item    ON jobWorkChallanLine(inventoryItemId);
