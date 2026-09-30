-- Phase F step 3 — stock gets a place, and stock levels get one per warehouse.
--
-- V164 recorded where a *movement* happened. It did not record where stock *is*: an
-- InventoryInstance is the physical unit on a shelf, and it had no location at all. Without
-- that, "how much of this item is in MAIN" has no answer to derive from, and picking in a
-- later phase has nothing to pick against.
--
-- Two things here:
--   1. inventoryinstance gains a warehouse (NOT NULL) and an optional storage location.
--   2. itemwarehousestock holds the per item-and-warehouse counters.
--
-- On (2) and why it is a counter rather than a view: untracked items carry no instance rows at
-- all, so a derived-only figure would read zero for most of the item master. The safeguard is an
-- invariant that holds regardless of instances -- the company-wide counter on
-- productinventorysettings must equal the sum of this table's rows for that item. That is
-- checkable for every item, tracked or not, and the reconciliation report enforces it.

-- 1. Where each instance sits -------------------------------------------------

ALTER TABLE inventoryinstance ADD COLUMN warehouse_id      BIGINT;
ALTER TABLE inventoryinstance ADD COLUMN storagelocation_id BIGINT;

UPDATE inventoryinstance SET warehouse_id = (SELECT id FROM warehouse WHERE code = 'MAIN')
 WHERE warehouse_id IS NULL;

ALTER TABLE inventoryinstance ALTER COLUMN warehouse_id SET NOT NULL;

ALTER TABLE inventoryinstance ADD CONSTRAINT fk_inventoryinstance_warehouse
    FOREIGN KEY (warehouse_id) REFERENCES warehouse (id);
ALTER TABLE inventoryinstance ADD CONSTRAINT fk_inventoryinstance_storagelocation
    FOREIGN KEY (storagelocation_id) REFERENCES storagelocation (id);

CREATE INDEX idx_inventoryinstance_warehouse ON inventoryinstance (warehouse_id);
CREATE INDEX idx_inventoryinstance_location  ON inventoryinstance (storagelocation_id)
    WHERE storagelocation_id IS NOT NULL;

COMMENT ON COLUMN inventoryinstance.warehouse_id IS
    'Which warehouse this physical stock is in. NOT NULL: stock always sits somewhere.';
COMMENT ON COLUMN inventoryinstance.storagelocation_id IS
    'Bin within the warehouse. Null unless the warehouse is bin-tracked, which is off by default.';

-- 2. Per item-and-warehouse stock levels --------------------------------------

CREATE TABLE itemwarehousestock (
    id               BIGSERIAL      PRIMARY KEY,
    inventoryItemRef INTEGER        NOT NULL REFERENCES inventoryItem (inventoryItemId),
    warehouse_id     BIGINT         NOT NULL REFERENCES warehouse (id),
    onHand           NUMERIC(18,4)  NOT NULL DEFAULT 0,
    reserved         NUMERIC(18,4)  NOT NULL DEFAULT 0,
    creationDate     TIMESTAMP,
    updatedDate      TIMESTAMP
);

COMMENT ON TABLE itemwarehousestock IS
    'Stock levels split by warehouse. The company-wide totals on productinventorysettings stay as
     the rollup, and must equal the sum of the rows here for the same item -- an invariant the
     stock reconciliation report checks, because an unreconcilable counter is what caused the
     drift this phase was built on top of.';

COMMENT ON COLUMN itemwarehousestock.onHand IS
    'Free to allocate in this warehouse. Mirrors productinventorysettings.availableQuantity, split by place.';

-- One row per item per warehouse; the counters live in that row rather than in duplicates.
CREATE UNIQUE INDEX ux_itemwarehousestock_item_warehouse
    ON itemwarehousestock (inventoryItemRef, warehouse_id);

CREATE INDEX idx_itemwarehousestock_warehouse ON itemwarehousestock (warehouse_id);

-- Seed the split from the existing company-wide totals: every item that currently reports stock
-- is, by definition, holding it in the default warehouse, since that is the only one that existed
-- until V163. Items at zero get no row -- rows appear when stock first moves into a warehouse.
INSERT INTO itemwarehousestock (inventoryItemRef, warehouse_id, onHand, reserved, creationDate)
SELECT s.inventory_item_id,
       (SELECT id FROM warehouse WHERE code = 'MAIN'),
       s.availableQuantity,
       s.reservedQuantity,
       NOW()
  FROM productinventorysettings s
 WHERE s.availableQuantity <> 0 OR s.reservedQuantity <> 0;
