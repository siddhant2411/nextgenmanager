-- V71: Rename opaque abbreviations in quotation table to readable names
-- Keep physical column names lowercase to match the baseline PostgreSQL schema.

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'public'
          AND table_name = 'quotation'
          AND column_name = 'pandfcharges'
    ) THEN
        ALTER TABLE quotation
            RENAME COLUMN pandfcharges TO packagingandforwardingcharges;
    END IF;
END $$;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'public'
          AND table_name = 'quotation'
          AND column_name = 'pandfchargespercentage'
    ) THEN
        ALTER TABLE quotation
            RENAME COLUMN pandfchargespercentage TO packagingandforwardingchargespercentage;
    END IF;
END $$;
