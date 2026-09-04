-- Phase F step 3b — moving stock between warehouses.
--
-- Warehouses that cannot exchange stock are just labels. This adds the document that moves it,
-- in two steps rather than one: dispatch takes stock out of the source, receipt puts it into the
-- destination, and the gap between them is real -- goods on a vehicle have left one store and not
-- yet arrived at the other.
--
-- That gap is why itemwarehousestock gains an inTransit column. The invariant introduced in V165
-- is that an item's company-wide counter equals the sum of its per-warehouse rows; a dispatch that
-- only decremented the source would break it for as long as the lorry is moving, and an invariant
-- that is allowed to be false sometimes stops being useful as a check. Counting in-transit keeps
-- it exactly true at every step:
--
--     availableQuantity  ==  SUM(onHand) + SUM(inTransit)
--
-- The alternative -- a fake TRANSIT warehouse -- keeps the arithmetic but puts a place in the
-- warehouse master that nobody can walk into. A column is the more honest shape.

ALTER TABLE itemwarehousestock ADD COLUMN inTransit NUMERIC(18,4) NOT NULL DEFAULT 0;

COMMENT ON COLUMN itemwarehousestock.inTransit IS
    'Dispatched from this warehouse but not yet received elsewhere. Still owned, still counted in
     the company-wide total, no longer available to pick here.';

CREATE TABLE stocktransfer (
    id                  BIGSERIAL     PRIMARY KEY,
    transferNumber      VARCHAR(30)   NOT NULL,
    fromWarehouse_id    BIGINT        NOT NULL REFERENCES warehouse (id),
    toWarehouse_id      BIGINT        NOT NULL REFERENCES warehouse (id),
    status              VARCHAR(20)   NOT NULL DEFAULT 'DRAFT'
        CHECK (status IN ('DRAFT','DISPATCHED','RECEIVED','CANCELLED')),
    dispatchedDate      TIMESTAMP,
    receivedDate        TIMESTAMP,
    remarks             VARCHAR(500),
    createdBy           VARCHAR(100),
    creationDate        TIMESTAMP,
    updatedDate         TIMESTAMP,
    deletedDate         TIMESTAMP,

    -- A transfer to the place it came from is a no-op that would still move counters twice.
    CONSTRAINT ck_stocktransfer_distinct_warehouses CHECK (fromWarehouse_id <> toWarehouse_id)
);

CREATE UNIQUE INDEX ux_stocktransfer_number
    ON stocktransfer (transferNumber)
    WHERE deletedDate IS NULL;

CREATE INDEX idx_stocktransfer_from   ON stocktransfer (fromWarehouse_id);
CREATE INDEX idx_stocktransfer_to     ON stocktransfer (toWarehouse_id);
CREATE INDEX idx_stocktransfer_status ON stocktransfer (status) WHERE deletedDate IS NULL;

COMMENT ON COLUMN stocktransfer.status IS
    'DRAFT moves nothing. DISPATCHED has left the source and counts as inTransit there.
     RECEIVED has landed in the destination. CANCELLED is only allowed from DRAFT.';

CREATE TABLE stocktransferline (
    id                BIGSERIAL      PRIMARY KEY,
    stocktransfer_id  BIGINT         NOT NULL REFERENCES stocktransfer (id),
    inventoryItemRef  INTEGER        NOT NULL REFERENCES inventoryItem (inventoryItemId),
    quantity          NUMERIC(18,4)  NOT NULL,
    receivedQuantity  NUMERIC(18,4)  NOT NULL DEFAULT 0,
    remarks           VARCHAR(300),

    CONSTRAINT ck_stocktransferline_qty_positive CHECK (quantity > 0),
    -- Short receipts are legitimate (damage, miscount); over-receipts are not.
    CONSTRAINT ck_stocktransferline_received_range CHECK (receivedQuantity >= 0 AND receivedQuantity <= quantity)
);

CREATE INDEX idx_stocktransferline_transfer ON stocktransferline (stocktransfer_id);
CREATE INDEX idx_stocktransferline_item     ON stocktransferline (inventoryItemRef);

COMMENT ON COLUMN stocktransferline.receivedQuantity IS
    'What actually arrived. Anything dispatched but never received stays inTransit at the source
     rather than silently evaporating -- a shortfall is a fact to investigate, not a rounding.';

-- Numbering (ST/0001) uses the existing NumberSequence table. No seed row here on purpose:
-- every other generator in the codebase creates its sequence lazily on first use, and seeding
-- one here would be the odd one out.
