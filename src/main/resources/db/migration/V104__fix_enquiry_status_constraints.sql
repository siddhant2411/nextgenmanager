-- Update status check constraint to include new CRM stages
-- We drop the constraint first because it was likely created by Hibernate with a fixed set of values
ALTER TABLE enquiry DROP CONSTRAINT IF EXISTS enquiry_status_check;
ALTER TABLE enquiry ADD CONSTRAINT enquiry_status_check CHECK (status IN ('NEW', 'CONTACTED', 'QUALIFIED', 'QUOTED', 'NEGOTIATION', 'FOLLOW_UP', 'CONVERTED', 'CLOSED', 'LOST', 'JUNK'));

-- Add check constraints for new enum columns added in V103
ALTER TABLE enquiry DROP CONSTRAINT IF EXISTS enquiry_priority_check;
ALTER TABLE enquiry ADD CONSTRAINT enquiry_priority_check CHECK (priority IN ('HOT', 'WARM', 'COLD'));

ALTER TABLE enquiry DROP CONSTRAINT IF EXISTS enquiry_type_check;
ALTER TABLE enquiry ADD CONSTRAINT enquiry_type_check CHECK (type IN ('PRODUCT', 'SERVICE', 'MIXED'));

-- Add check constraint for conversation type
ALTER TABLE enquiryConversationRecord DROP CONSTRAINT IF EXISTS enquiry_conversation_type_check;
ALTER TABLE enquiryConversationRecord ADD CONSTRAINT enquiry_conversation_type_check CHECK (conversationType IN ('CALL', 'EMAIL', 'MEETING', 'NOTE'));
