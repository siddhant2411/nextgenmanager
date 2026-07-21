-- Add area units (SQFT, SQM, SQIN) to the item UOM enum.
--
-- inventoryItem.uom is persisted as an enum ORDINAL (smallint). The CHECK constraint from
-- V145 caps it at 0..8 (the 9 original UOM values: NOS..SET). The three new enum constants
-- are APPENDED at the end of UOM at ordinals 9, 10, 11, so widen the constraint to 0..11.
--
-- New UOM values must always be appended at the end of the enum — inserting mid-list would
-- shift existing ordinals and silently corrupt every stored inventoryItem.uom.
ALTER TABLE inventoryItem DROP CONSTRAINT IF EXISTS inventoryitem_uom_check;
ALTER TABLE inventoryItem ADD  CONSTRAINT inventoryitem_uom_check CHECK (uom >= 0 AND uom <= 11);
