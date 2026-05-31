-- V127: V71 renamed pandfcharges -> packagingandforwardingcharges but filtered
-- by `table_schema = 'public'`, so tenant schemas provisioned via the multi-tenant
-- flow still have the old `pandfcharges` columns. This migration retries the
-- rename in the *current* schema so it applies wherever Flyway is run.

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = current_schema()
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
        WHERE table_schema = current_schema()
          AND table_name = 'quotation'
          AND column_name = 'pandfchargespercentage'
    ) THEN
        ALTER TABLE quotation
            RENAME COLUMN pandfchargespercentage TO packagingandforwardingchargespercentage;
    END IF;
END $$;
