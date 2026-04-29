-- Track stock committed to active orders (WO / SO) separately from free available stock.
-- availableQuantity + reservedQuantity = total physical stock on hand.
ALTER TABLE productInventorySettings
    ADD COLUMN IF NOT EXISTS reservedQuantity DOUBLE PRECISION NOT NULL DEFAULT 0;
