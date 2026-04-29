-- Adds financial/valuation fields to the inventory ledger for GRN and stock value tracking

ALTER TABLE inventoryLedger
    ADD COLUMN IF NOT EXISTS rate float8 NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS amount float8 NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS valuationMethod varchar(20) DEFAULT 'AVERAGE',
    ADD COLUMN IF NOT EXISTS warehouse varchar(100),
    ADD COLUMN IF NOT EXISTS referenceType varchar(50),
    ADD COLUMN IF NOT EXISTS createdBy varchar(100);
