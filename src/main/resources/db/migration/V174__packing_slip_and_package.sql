-- Phase J — packaging and package QC.
--
-- Picking (phase I) chooses which units leave the shelf; nothing records what physically went into
-- which box. This adds that: a packing slip per pick, one or more boxes on it, and a line per box
-- tying picked stock to the box it went into. Package QC reuses the phase H inspection lot rather
-- than inventing a second checklist — an InspectionLot with source PACKAGE, against a box, and the
-- slip cannot close until every box that was inspected has cleared.
--
-- The box table is named packagebox, not package -- java.lang.Package already owns that name, and
-- giving the entity a name that shadows a JDK class is asking for a confusing bug later for no
-- benefit now.
--
-- Deliberately NOT included here: wiring the delivery note to require a closed slip. The DN still
-- consumes the pick directly, the same bypass phase I shipped with. Making packing a required hop
-- between pick and dispatch is its own step, the same way the DN's pick-consumption was phase I's
-- step 2.

CREATE TABLE packingslip (
    id            BIGSERIAL     PRIMARY KEY,
    slipNumber    VARCHAR(30)   NOT NULL,
    salesOrder_id BIGINT        NOT NULL REFERENCES salesOrder (id),
    picklist_id   BIGINT        NOT NULL REFERENCES picklist (id),
    status        VARCHAR(20)   NOT NULL DEFAULT 'DRAFT'
        CHECK (status IN ('DRAFT','PACKED','CLOSED','CANCELLED')),
    packedDate    TIMESTAMP,
    closedDate    TIMESTAMP,
    packedBy      VARCHAR(100),
    closedBy      VARCHAR(100),
    remarks       VARCHAR(500),
    createdBy     VARCHAR(100),
    creationDate  TIMESTAMP,
    updatedDate   TIMESTAMP,
    deletedDate   TIMESTAMP
);

COMMENT ON COLUMN packingslip.status IS
    'DRAFT is being packed. PACKED has every box recorded and is waiting on any package QC it
     raised. CLOSED is done and ready to ship. CANCELLED releases nothing by itself -- the pick
     behind it is untouched.';
COMMENT ON COLUMN packingslip.picklist_id IS
    'The confirmed pick being packed. One slip per pick, the same one-trip reasoning as the pick
     itself having one warehouse.';

CREATE UNIQUE INDEX ux_packingslip_number ON packingslip (slipNumber) WHERE deletedDate IS NULL;

-- One live slip per pick. A cancelled slip is excluded because cancelling released its box
-- allocations -- the pick is free to be packed again, and without this the first cancel would
-- strand that pick permanently.
CREATE UNIQUE INDEX ux_packingslip_picklist ON packingslip (picklist_id)
    WHERE deletedDate IS NULL AND status <> 'CANCELLED';
CREATE INDEX idx_packingslip_salesorder ON packingslip (salesOrder_id);
CREATE INDEX idx_packingslip_status ON packingslip (status) WHERE deletedDate IS NULL;

CREATE TABLE packagebox (
    id             BIGSERIAL     PRIMARY KEY,
    packingslip_id BIGINT        NOT NULL REFERENCES packingslip (id),
    boxNumber      INTEGER       NOT NULL,
    boxType        VARCHAR(50),
    lengthCm       NUMERIC(18,4),
    widthCm        NUMERIC(18,4),
    heightCm       NUMERIC(18,4),
    grossWeightKg  NUMERIC(18,4),
    netWeightKg    NUMERIC(18,4),
    shippingMarks  VARCHAR(500),
    creationDate   TIMESTAMP,
    updatedDate    TIMESTAMP,
    deletedDate    TIMESTAMP
);

-- Partial, not a table constraint: boxNumber is handed out by MAX(boxNumber) + 1 over live boxes
-- only, so a soft-deleted box must not keep reserving the number it used to hold.
CREATE UNIQUE INDEX ux_packagebox_slip_box ON packagebox (packingslip_id, boxNumber)
    WHERE deletedDate IS NULL;

COMMENT ON COLUMN packagebox.boxNumber IS
    'Sequential within its slip, assigned by the service -- 1, 2, 3 -- not a global number. What
     the label on the box says.';

CREATE INDEX idx_packagebox_slip ON packagebox (packingslip_id);

CREATE TABLE packageline (
    id               BIGSERIAL      PRIMARY KEY,
    packagebox_id    BIGINT         NOT NULL REFERENCES packagebox (id),
    picklistline_id  BIGINT         REFERENCES picklistline (id),
    inventoryItemRef INTEGER        NOT NULL REFERENCES inventoryItem (inventoryItemId),
    quantity         NUMERIC(18,4)  NOT NULL,

    CONSTRAINT ck_packageline_qty_positive CHECK (quantity > 0)
);

COMMENT ON COLUMN packageline.picklistline_id IS
    'Which pick line this came from, when the item was picked through phase I. Null for goods
     boxed outside a pick.';

CREATE INDEX idx_packageline_box  ON packageline (packagebox_id);
CREATE INDEX idx_packageline_item ON packageline (inventoryItemRef);

-- Which physical units went into which box. An instance belongs to at most one package line, the
-- same reasoning as picklistline_id on this table.
ALTER TABLE inventoryinstance ADD COLUMN packageline_id BIGINT;

ALTER TABLE inventoryinstance ADD CONSTRAINT fk_inventoryinstance_packageline
    FOREIGN KEY (packageline_id) REFERENCES packageline (id);

CREATE INDEX idx_inventoryinstance_packageline
    ON inventoryinstance (packageline_id)
    WHERE packageline_id IS NOT NULL;

COMMENT ON COLUMN inventoryinstance.packageline_id IS
    'The box this unit was packed into, if any.';

-- Package QC. Phase H shipped the PACKAGE source with no document column behind it yet.
ALTER TABLE inspectionlot ADD COLUMN packagebox_id BIGINT REFERENCES packagebox (id);

CREATE INDEX idx_inspectionlot_packagebox ON inspectionlot (packagebox_id) WHERE packagebox_id IS NOT NULL;

ALTER TABLE inspectionlot DROP CONSTRAINT ck_inspectionlot_source_document;
ALTER TABLE inspectionlot ADD CONSTRAINT ck_inspectionlot_source_document CHECK (
    (source = 'INCOMING'   AND goodsReceiptNote_id IS NOT NULL)
 OR (source = 'IN_PROCESS' AND workOrderOperation_id IS NOT NULL)
 OR (source = 'FINAL'      AND workOrder_id IS NOT NULL)
 OR (source = 'PACKAGE'    AND packagebox_id IS NOT NULL));
