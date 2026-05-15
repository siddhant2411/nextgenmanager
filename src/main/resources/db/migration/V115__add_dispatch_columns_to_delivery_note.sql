-- V115: Add dispatch / transport columns introduced in DeliveryNote entity
-- These are referenced by sales/model/DeliveryNote.java but were missing from the baseline schema.

ALTER TABLE deliverynote ADD COLUMN IF NOT EXISTS vehicleNumber  VARCHAR(50);
ALTER TABLE deliverynote ADD COLUMN IF NOT EXISTS ewayBillNumber VARCHAR(50);
ALTER TABLE deliverynote ADD COLUMN IF NOT EXISTS dispatchThrough VARCHAR(100);
ALTER TABLE deliverynote ADD COLUMN IF NOT EXISTS remarks         VARCHAR(500);
