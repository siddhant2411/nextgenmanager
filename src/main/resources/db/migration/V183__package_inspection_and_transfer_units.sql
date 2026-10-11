-- Two gaps left by the packaging and stock-transfer phases.
--
-- 1. A final inspection can be made compulsory per item (V169); a package inspection could not.
--    A box was inspected only if somebody remembered to ask. The flag is off by default, so
--    nothing changes for an item nobody switches it on for.
--
-- 2. A stock transfer moved the per-warehouse counters and nothing else. Batch and serial units
--    kept pointing at the warehouse they left, so at the destination the counter said the stock
--    was there while no unit could be picked. A unit now carries the transfer line that is moving
--    it: set at dispatch, cleared at receipt when the unit's warehouse is changed.

ALTER TABLE productInventorySettings
    ADD COLUMN packageInspectionRequired BOOLEAN NOT NULL DEFAULT FALSE;

COMMENT ON COLUMN productInventorySettings.packageInspectionRequired IS
    'When true, a packing slip cannot close while a box holding this item has no passed or waived package inspection for it.';

ALTER TABLE inventoryInstance ADD COLUMN stocktransferline_id BIGINT;

ALTER TABLE inventoryInstance ADD CONSTRAINT fk_inventoryinstance_stocktransferline
    FOREIGN KEY (stocktransferline_id) REFERENCES stocktransferline (id);

CREATE INDEX idx_inventoryinstance_stocktransferline ON inventoryInstance (stocktransferline_id);

COMMENT ON COLUMN inventoryInstance.stocktransferline_id IS
    'The stock transfer line carrying this unit between warehouses. Set at dispatch, cleared at receipt. A unit in transit cannot be picked.';
