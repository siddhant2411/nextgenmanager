-- Add CRM pipeline fields to the enquiry table
ALTER TABLE enquiry
    ADD COLUMN IF NOT EXISTS opportunityname VARCHAR(255),
    ADD COLUMN IF NOT EXISTS expectedrevenue NUMERIC(15, 2) DEFAULT 0,
    ADD COLUMN IF NOT EXISTS probability INTEGER,
    ADD COLUMN IF NOT EXISTS targetclosedate DATE;
