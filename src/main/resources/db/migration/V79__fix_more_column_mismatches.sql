-- V79: Fix additional legacy column mismatches in Delivery and Inventory modules

-- Delivery Note line items
ALTER TABLE IF EXISTS deliverynoteitem RENAME COLUMN inventoryitem_inventoryitemid TO inventory_item_id;
ALTER TABLE IF EXISTS deliverynoteitem RENAME COLUMN deliverynote_id TO delivery_note_id;

-- Inventory Instance (matching Request ID mapping)
DO $$ 
BEGIN 
    IF EXISTS (SELECT 1 FROM information_schema.columns 
               WHERE table_name='inventoryinstance' AND column_name='inventoryitemrequest') THEN
        ALTER TABLE inventoryinstance RENAME COLUMN inventoryitemrequest TO inventoryRequestId;
    END IF;
END $$;
