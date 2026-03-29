-- V72: Change Enquiry and Quotation primary keys from INTEGER to BIGINT
-- This aligns later migrations with the lowercase identifiers created in V1.

-- enquiry table
ALTER TABLE IF EXISTS enquiry ALTER COLUMN id TYPE BIGINT;

-- enquiredproducts table
ALTER TABLE IF EXISTS enquiredproducts ALTER COLUMN id TYPE BIGINT;
ALTER TABLE IF EXISTS enquiredproducts ALTER COLUMN enquiry_id TYPE BIGINT;

-- enquiryconversationrecord table
ALTER TABLE IF EXISTS enquiryconversationrecord ALTER COLUMN id TYPE BIGINT;
ALTER TABLE IF EXISTS enquiryconversationrecord ALTER COLUMN enquiry_conversation_id TYPE BIGINT;

-- quotation table
ALTER TABLE IF EXISTS quotation ALTER COLUMN id TYPE BIGINT;
ALTER TABLE IF EXISTS quotation ALTER COLUMN enquiry_id TYPE BIGINT;

-- quotation_products table
ALTER TABLE IF EXISTS quotation_products ALTER COLUMN id TYPE BIGINT;
ALTER TABLE IF EXISTS quotation_products ALTER COLUMN quotation_id TYPE BIGINT;
