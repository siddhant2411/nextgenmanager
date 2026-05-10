-- V100: Batch and Serial Number tracking for inventory

-- Sequence table — used by BatchSerialServiceImpl to atomically generate unique numbers
CREATE TABLE numberSequence (
    sequenceKey VARCHAR(120) PRIMARY KEY,
    nextVal     BIGINT       NOT NULL DEFAULT 1
);

-- Batch master — one row per production/receipt batch
CREATE TABLE batchNumber (
    id               BIGSERIAL     PRIMARY KEY,
    batchNumber      VARCHAR(80)   NOT NULL UNIQUE,
    inventoryItemRef INTEGER       NOT NULL REFERENCES inventoryItem(inventoryItemId),
    manufacturingDate DATE,
    expiryDate       DATE,
    supplierBatchNo  VARCHAR(100),
    originalQty      NUMERIC(15,5) NOT NULL DEFAULT 0,
    remainingQty     NUMERIC(15,5) NOT NULL DEFAULT 0,
    status           VARCHAR(20)   NOT NULL DEFAULT 'ACTIVE',
    source           VARCHAR(30)   NOT NULL,
    sourceDocNo      VARCHAR(100),
    warehouse        VARCHAR(100),
    createdBy        VARCHAR(100),
    createdDate      TIMESTAMP     NOT NULL DEFAULT NOW()
);

-- Serial number master — one row per physical unit
CREATE TABLE serialNumber (
    id               BIGSERIAL     PRIMARY KEY,
    serialNumber     VARCHAR(100)  NOT NULL UNIQUE,
    inventoryItemRef INTEGER       NOT NULL REFERENCES inventoryItem(inventoryItemId),
    batchId          BIGINT        REFERENCES batchNumber(id),
    status           VARCHAR(20)   NOT NULL DEFAULT 'AVAILABLE',
    receivedDate     DATE,
    source           VARCHAR(30)   NOT NULL,
    sourceDocNo      VARCHAR(100),
    warehouse        VARCHAR(100),
    consumedDate     TIMESTAMP,
    consumedByDocNo  VARCHAR(100),
    createdBy        VARCHAR(100),
    createdDate      TIMESTAMP     NOT NULL DEFAULT NOW()
);

-- Link inventoryInstance to batch and serial
ALTER TABLE inventoryInstance ADD COLUMN batchId  BIGINT REFERENCES batchNumber(id);
ALTER TABLE inventoryInstance ADD COLUMN serialId BIGINT UNIQUE REFERENCES serialNumber(id);

-- Indexes
CREATE INDEX idx_batch_item    ON batchNumber(inventoryItemRef);
CREATE INDEX idx_batch_status  ON batchNumber(status);
CREATE INDEX idx_batch_expiry  ON batchNumber(expiryDate);
CREATE INDEX idx_serial_item   ON serialNumber(inventoryItemRef);
CREATE INDEX idx_serial_status ON serialNumber(status);
CREATE INDEX idx_serial_batch  ON serialNumber(batchId);
CREATE INDEX idx_invinst_batch ON inventoryInstance(batchId);
