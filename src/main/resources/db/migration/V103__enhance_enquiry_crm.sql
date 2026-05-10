-- Enhance Enquiry with CRM fields
ALTER TABLE enquiry
    ADD COLUMN IF NOT EXISTS priority VARCHAR(20) DEFAULT 'WARM',
    ADD COLUMN IF NOT EXISTS type VARCHAR(20) DEFAULT 'PRODUCT',
    ADD COLUMN IF NOT EXISTS city VARCHAR(100),
    ADD COLUMN IF NOT EXISTS state VARCHAR(100),
    ADD COLUMN IF NOT EXISTS assigned_to_id BIGINT,
    ADD COLUMN IF NOT EXISTS leadQuality VARCHAR(50);

-- Add constraint for assigned_to_id
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_enquiry_assigned_to') THEN
        ALTER TABLE enquiry
            ADD CONSTRAINT fk_enquiry_assigned_to FOREIGN KEY (assigned_to_id) REFERENCES appuser(id);
    END IF;
END $$;

-- Add conversationType to enquiryConversationRecord
ALTER TABLE enquiryConversationRecord
    ADD COLUMN IF NOT EXISTS conversationType VARCHAR(20) DEFAULT 'NOTE';
