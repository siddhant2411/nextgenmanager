-- Add currency column to quotation table
ALTER TABLE quotation ADD COLUMN IF NOT EXISTS currency VARCHAR(10) DEFAULT 'INR';

-- Update quotation status check constraint to include REVISED
ALTER TABLE quotation DROP CONSTRAINT IF EXISTS quotation_quotationstatus_check;
ALTER TABLE quotation ADD CONSTRAINT quotation_quotationstatus_check CHECK (quotationstatus IN ('DRAFT', 'SENT', 'ACCEPTED', 'REJECTED', 'REVISED'));
