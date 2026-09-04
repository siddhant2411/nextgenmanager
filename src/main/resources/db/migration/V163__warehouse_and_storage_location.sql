-- Phase F step 1 — warehouse master and storage locations.
--
-- Until now "warehouse" was a free-text VARCHAR repeated on inventoryledger, goodsreceiptnote,
-- batchnumber and serialnumber, with no master behind it: nothing constrained the spelling, and
-- there was no way to ask what warehouses exist, move stock between them, or quarantine a
-- rejected receipt. This adds the master. Converting those four columns to foreign keys is a
-- separate migration, deliberately kept apart so this one is purely additive.
--
-- Bins are modelled now but off by default (warehouse.binTracked = FALSE). Retrofitting a
-- location level later would mean touching every stock query a second time; shipping the table
-- unused costs almost nothing.

CREATE TABLE warehouse (
    id             BIGSERIAL     PRIMARY KEY,
    code           VARCHAR(20)   NOT NULL,
    name           VARCHAR(120)  NOT NULL,
    warehouseType  VARCHAR(20)   NOT NULL DEFAULT 'GENERAL'
        CHECK (warehouseType IN ('GENERAL','RAW_MATERIAL','WIP','FINISHED_GOODS','QUARANTINE','SCRAP')),
    addressLine1   VARCHAR(200),
    addressLine2   VARCHAR(200),
    city           VARCHAR(100),
    state          VARCHAR(100),
    pincode        VARCHAR(10),
    gstin          VARCHAR(15),
    isDefault      BOOLEAN       NOT NULL DEFAULT FALSE,
    binTracked     BOOLEAN       NOT NULL DEFAULT FALSE,
    active         BOOLEAN       NOT NULL DEFAULT TRUE,
    creationDate   TIMESTAMP,
    updatedDate    TIMESTAMP,
    deletedDate    TIMESTAMP
);

COMMENT ON COLUMN warehouse.warehouseType IS
    'Drives routing rather than reporting: QUARANTINE receives GRN-rejected quantity, SCRAP receives write-offs.';
COMMENT ON COLUMN warehouse.binTracked IS
    'When false the warehouse is a single bucket and storagelocation rows are not required. Off by default.';
COMMENT ON COLUMN warehouse.gstin IS
    'Only set where a warehouse files under a different GSTIN. Null means the company GSTIN applies.';

-- Codes are the human handle used on documents, so they must stay unique among live rows.
-- Soft-deleted rows are excluded so a code can be retired and later reused.
CREATE UNIQUE INDEX ux_warehouse_code
    ON warehouse (code)
    WHERE deletedDate IS NULL;

-- Exactly one default at a time. The partial index does the enforcing so no service code has to.
CREATE UNIQUE INDEX ux_warehouse_single_default
    ON warehouse (isDefault)
    WHERE isDefault AND deletedDate IS NULL;

CREATE TABLE storagelocation (
    id            BIGSERIAL     PRIMARY KEY,
    warehouse_id  BIGINT        NOT NULL REFERENCES warehouse (id),
    code          VARCHAR(40)   NOT NULL,
    aisle         VARCHAR(20),
    rack          VARCHAR(20),
    bin           VARCHAR(20),
    pickable      BOOLEAN       NOT NULL DEFAULT TRUE,
    active        BOOLEAN       NOT NULL DEFAULT TRUE,
    creationDate  TIMESTAMP,
    updatedDate   TIMESTAMP,
    deletedDate   TIMESTAMP
);

COMMENT ON COLUMN storagelocation.code IS
    'Short label picked from and printed on a pick list, e.g. A-03-2. Unique within its warehouse.';
COMMENT ON COLUMN storagelocation.pickable IS
    'False for staging, inspection or damage locations that hold stock but must never be picked from.';

CREATE UNIQUE INDEX ux_storagelocation_warehouse_code
    ON storagelocation (warehouse_id, code)
    WHERE deletedDate IS NULL;

CREATE INDEX idx_storagelocation_warehouse
    ON storagelocation (warehouse_id)
    WHERE deletedDate IS NULL;

-- One default warehouse so every existing flow has somewhere to point the moment the
-- free-text columns become foreign keys. Deliberately generic: the real store names are
-- company data, not schema.
INSERT INTO warehouse (code, name, warehouseType, isDefault, binTracked, active, creationDate)
VALUES ('MAIN', 'Main Store', 'GENERAL', TRUE, FALSE, TRUE, NOW());

-- Quarantine exists from the start because GRN already records a rejectedQty that currently
-- goes nowhere. Phase H routes it here rather than letting it vanish.
INSERT INTO warehouse (code, name, warehouseType, isDefault, binTracked, active, creationDate)
VALUES ('QUAR', 'Quarantine', 'QUARANTINE', FALSE, FALSE, TRUE, NOW());
