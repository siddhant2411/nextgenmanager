-- V72: Change Enquiry and Quotation primary keys from INTEGER to BIGINT
-- This prevents integer overflow as data grows and aligns with SalesOrder which already uses BIGINT

-- enquiry table
ALTER TABLE enquiry ALTER COLUMN id TYPE BIGINT;

-- enquiredProducts table
ALTER TABLE "enquiredProducts" ALTER COLUMN id TYPE BIGINT;
ALTER TABLE "enquiredProducts" ALTER COLUMN enquiry_id TYPE BIGINT;

-- enquiryConversationRecord table
ALTER TABLE "enquiryConversationRecord" ALTER COLUMN id TYPE BIGINT;
ALTER TABLE "enquiryConversationRecord" ALTER COLUMN enquiry_conversation_id TYPE BIGINT;

-- quotation table
ALTER TABLE quotation ALTER COLUMN id TYPE BIGINT;
ALTER TABLE quotation ALTER COLUMN enquiry_id TYPE BIGINT;

-- quotation_products table
ALTER TABLE quotation_products ALTER COLUMN id TYPE BIGINT;
ALTER TABLE quotation_products ALTER COLUMN quotation_id TYPE BIGINT;
