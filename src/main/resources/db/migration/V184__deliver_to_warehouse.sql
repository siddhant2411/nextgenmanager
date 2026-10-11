-- Where the goods are wanted is decided when the need is raised, and carried down from there.
--
-- Until now nothing upstream of the stock movement itself named a warehouse. A shortfall was
-- raised with no destination; the work order it became produced into whichever store its work
-- centre happened to belong to; the requisition it became had nowhere to say, its purchase order
-- was always addressed to the registered office, and the goods receipt was keyed by hand at the
-- gate. Each document guessed on its own, at the last moment.
--
-- Four columns, one idea. The sales order says which warehouse it will be dispatched from. A need
-- raised for it inherits that; a need raised from the stores says so itself. Whichever document
-- the need turns into -- work order or requisition -- copies it, and can still be changed there.
--
-- Null keeps the old behaviour on every one of them, so nothing changes for a single-site
-- installation: a sales order dispatches from the default warehouse, a need or requisition is for
-- the default warehouse, and a work order produces into the store it draws its material from.

ALTER TABLE salesOrder ADD COLUMN IF NOT EXISTS dispatchWarehouseId BIGINT REFERENCES warehouse (id);

ALTER TABLE inventoryProcurementOrder ADD COLUMN IF NOT EXISTS deliverToWarehouseId BIGINT REFERENCES warehouse (id);

ALTER TABLE purchaseRequisition ADD COLUMN IF NOT EXISTS deliverToWarehouseId BIGINT REFERENCES warehouse (id);

ALTER TABLE workOrder ADD COLUMN IF NOT EXISTS produceIntoWarehouseId BIGINT REFERENCES warehouse (id);

COMMENT ON COLUMN salesOrder.dispatchWarehouseId IS
    'The warehouse this order is dispatched from. Null means the default warehouse.';
COMMENT ON COLUMN inventoryProcurementOrder.deliverToWarehouseId IS
    'The warehouse the shortfall is wanted in. Null (rows older than V184) means the default warehouse.';
COMMENT ON COLUMN purchaseRequisition.deliverToWarehouseId IS
    'The warehouse the goods are to be received into. Null means the default warehouse.';
COMMENT ON COLUMN workOrder.produceIntoWarehouseId IS
    'The warehouse finished goods are booked into. Null means the store the order draws its material from.';
