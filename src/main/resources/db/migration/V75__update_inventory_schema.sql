-- Add allowNegativeStock to productInventorySettings
ALTER TABLE productInventorySettings ADD COLUMN allowNegativeStock BOOLEAN NOT NULL DEFAULT FALSE;

-- Add overrideReason to inventoryLedger
ALTER TABLE inventoryLedger ADD COLUMN overrideReason VARCHAR(255);

-- Fix types for inventoryLedger to match double in Java
ALTER TABLE inventoryLedger ALTER COLUMN quantity TYPE float8;
ALTER TABLE inventoryLedger ALTER COLUMN closingbalance TYPE float8;
