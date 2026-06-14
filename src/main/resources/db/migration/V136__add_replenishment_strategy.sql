-- Replenishment route per item: MAKE_TO_STOCK (reserve from stock, reorder rules replenish)
-- vs MAKE_TO_ORDER (shortfall raises an order-linked procurement need).
-- Default existing items to MAKE_TO_STOCK to preserve current reserve-from-stock behaviour.
ALTER TABLE productInventorySettings
    ADD COLUMN IF NOT EXISTS replenishmentStrategy VARCHAR(20) NOT NULL DEFAULT 'MAKE_TO_STOCK';
