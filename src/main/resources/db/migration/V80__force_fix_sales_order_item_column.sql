-- V80: Final force-fix for salesorderitem mapping
DO $$ 
BEGIN 
    -- Check if the legacy name exists (case-insensitive check)
    IF EXISTS (SELECT 1 FROM information_schema.columns 
               WHERE table_name='salesorderitem' AND LOWER(column_name)='inventoryitemid') THEN
        
        -- We try renaming both lowercase and camelcase variations
        BEGIN
            ALTER TABLE salesorderitem RENAME COLUMN "inventoryitemid" TO "inventory_item_id";
        EXCEPTION WHEN OTHERS THEN
            BEGIN
                ALTER TABLE salesorderitem RENAME COLUMN "inventoryItemId" TO "inventory_item_id";
            EXCEPTION WHEN OTHERS THEN
                NULL; -- Already renamed or missing
            END;
        END;
    END IF;
END $$;
