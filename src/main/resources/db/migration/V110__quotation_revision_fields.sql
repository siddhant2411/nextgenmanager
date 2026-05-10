ALTER TABLE quotation 
ADD COLUMN revisionNumber INTEGER DEFAULT 0,
ADD COLUMN parentQuotationId BIGINT;
