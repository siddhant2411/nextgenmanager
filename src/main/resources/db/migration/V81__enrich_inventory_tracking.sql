-- Add consumption tracking to inventory instances
ALTER TABLE inventoryInstance ADD COLUMN IF NOT EXISTS consumptionReferenceNo VARCHAR(255);

-- Add scrap tracking to inventory ledger
ALTER TABLE inventoryLedger ADD COLUMN IF NOT EXISTS scrappedQuantity DOUBLE PRECISION DEFAULT 0;
