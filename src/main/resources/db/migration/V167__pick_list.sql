-- Phase I step 1 — the pick list.
--
-- Dispatch currently jumps straight from "stock exists" to a delivery note, which allocates
-- inventory instances inline while it is also costing them, writing the ledger and recalculating
-- the sales order. There is no record of the physical act of picking: who went to which location,
-- what they actually found, and which specific batch or serial left the shelf.
--
-- This adds that record. Deliberately NOT included here:
--   * reservation. Stock is already reserved at sales-order approval; picking chooses WHICH
--     instances satisfy that reservation, it does not reserve again. Nothing in this migration
--     moves a counter, so the V165/V166 invariant cannot be disturbed by it.
--   * the delivery note. Making the DN consume a confirmed pick is a refactor of the busiest
--     method in the sales module and belongs in its own step.

CREATE TABLE picklist (
    id               BIGSERIAL     PRIMARY KEY,
    pickNumber       VARCHAR(30)   NOT NULL,
    salesOrder_id    BIGINT        NOT NULL REFERENCES salesOrder (id),
    warehouse_id     BIGINT        NOT NULL REFERENCES warehouse (id),
    status           VARCHAR(20)   NOT NULL DEFAULT 'DRAFT'
        CHECK (status IN ('DRAFT','RELEASED','PICKED','CANCELLED')),
    releasedDate     TIMESTAMP,
    pickedDate       TIMESTAMP,
    pickedBy         VARCHAR(100),
    remarks          VARCHAR(500),
    createdBy        VARCHAR(100),
    creationDate     TIMESTAMP,
    updatedDate      TIMESTAMP,
    deletedDate      TIMESTAMP
);

COMMENT ON COLUMN picklist.status IS
    'DRAFT is being prepared. RELEASED is on the floor to be picked. PICKED has specific instances
     allocated and is ready for a delivery note. CANCELLED releases any allocation.';
COMMENT ON COLUMN picklist.warehouse_id IS
    'Picked from one warehouse. An order spanning two warehouses is two pick lists, because it is
     two physical trips.';

CREATE UNIQUE INDEX ux_picklist_number ON picklist (pickNumber) WHERE deletedDate IS NULL;
CREATE INDEX idx_picklist_salesorder ON picklist (salesOrder_id);
CREATE INDEX idx_picklist_warehouse  ON picklist (warehouse_id);
CREATE INDEX idx_picklist_status     ON picklist (status) WHERE deletedDate IS NULL;

CREATE TABLE picklistline (
    id                 BIGSERIAL      PRIMARY KEY,
    picklist_id        BIGINT         NOT NULL REFERENCES picklist (id),
    inventoryItemRef   INTEGER        NOT NULL REFERENCES inventoryItem (inventoryItemId),
    salesOrderItem_id  BIGINT         REFERENCES salesOrderItem (id),
    storagelocation_id BIGINT         REFERENCES storagelocation (id),
    quantityToPick     NUMERIC(18,4)  NOT NULL,
    quantityPicked     NUMERIC(18,4)  NOT NULL DEFAULT 0,
    remarks            VARCHAR(300),

    CONSTRAINT ck_picklistline_topick_positive CHECK (quantityToPick > 0),
    -- A short pick is a fact worth keeping; picking more than the order asked for is not.
    CONSTRAINT ck_picklistline_picked_range CHECK (quantityPicked >= 0 AND quantityPicked <= quantityToPick)
);

COMMENT ON COLUMN picklistline.quantityPicked IS
    'What the picker actually found. Less than quantityToPick is a short pick, kept as a fact to
     act on rather than silently reduced.';
COMMENT ON COLUMN picklistline.storagelocation_id IS
    'Where to go. Null when the warehouse is not bin-tracked, which is the default.';

CREATE INDEX idx_picklistline_picklist ON picklistline (picklist_id);
CREATE INDEX idx_picklistline_item     ON picklistline (inventoryItemRef);

-- Which physical units were taken. An instance belongs to at most one pick line, so this lives on
-- the instance rather than in a join table -- and unlike the existing salesOrderItemId and
-- deliveryNoteItemId columns beside it, which are bare Longs, this one is a real foreign key.
ALTER TABLE inventoryinstance ADD COLUMN picklistline_id BIGINT;

ALTER TABLE inventoryinstance ADD CONSTRAINT fk_inventoryinstance_picklistline
    FOREIGN KEY (picklistline_id) REFERENCES picklistline (id);

CREATE INDEX idx_inventoryinstance_picklistline
    ON inventoryinstance (picklistline_id)
    WHERE picklistline_id IS NOT NULL;

COMMENT ON COLUMN inventoryinstance.picklistline_id IS
    'The pick that allocated this unit. Cleared when the pick is cancelled, so allocation is
     never stranded on stock that is back on the shelf.';
