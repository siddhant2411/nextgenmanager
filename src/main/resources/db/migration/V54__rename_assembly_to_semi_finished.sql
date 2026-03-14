-- Rename ASSEMBLY item type to SEMI_FINISHED for string-backed enum storage.
-- If itemType is stored as ORDINAL (smallint/integer), no data rewrite is needed.
DO $$
DECLARE
    item_type_data_type text;
BEGIN
    SELECT c.data_type
    INTO item_type_data_type
    FROM information_schema.columns c
    WHERE c.table_schema = current_schema()
      AND c.table_name = 'inventoryitem'
      AND c.column_name = 'itemtype';

    IF item_type_data_type IN ('character varying', 'character', 'text') THEN
        UPDATE inventoryitem
        SET itemtype = 'SEMI_FINISHED'
        WHERE itemtype = 'ASSEMBLY';
    END IF;
END $$;
