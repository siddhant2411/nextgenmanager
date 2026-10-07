-- Bill-to and ship-to as two separate parties on sales documents.
--
-- Until now a sales order carried one free-text deliveryAddress, pre-filled with the customer's
-- first address, and the bill-to was looked up from the customer master every time a document was
-- printed. So the two blocks on an invoice were the same address twice, the consignee was always
-- the customer, and editing a customer's address changed invoices that had already been issued.
--
-- salesOrder keeps deliveryAddress as the ship-to address and gains the billing address chosen for
-- the order plus the consignee, for goods billed to one party and delivered to another. All four
-- are optional: blank means "the customer's billing address" and "ship to the bill-to party".
--
-- taxInvoice takes a copy of both parties when it is raised. An invoice is a legal document and
-- has to keep saying what it said on the day it was issued. Invoices raised before this migration
-- have no copy and go on reading from their sales order.

ALTER TABLE salesOrder ADD COLUMN IF NOT EXISTS billToAddress   VARCHAR(500);
ALTER TABLE salesOrder ADD COLUMN IF NOT EXISTS shipToName      VARCHAR(255);
ALTER TABLE salesOrder ADD COLUMN IF NOT EXISTS shipToGstin     VARCHAR(15);
ALTER TABLE salesOrder ADD COLUMN IF NOT EXISTS shipToStateCode VARCHAR(2);

ALTER TABLE taxInvoice ADD COLUMN IF NOT EXISTS billToName      VARCHAR(255);
ALTER TABLE taxInvoice ADD COLUMN IF NOT EXISTS billToAddress   VARCHAR(500);
ALTER TABLE taxInvoice ADD COLUMN IF NOT EXISTS billToGstin     VARCHAR(15);
ALTER TABLE taxInvoice ADD COLUMN IF NOT EXISTS billToStateCode VARCHAR(2);
ALTER TABLE taxInvoice ADD COLUMN IF NOT EXISTS shipToName      VARCHAR(255);
ALTER TABLE taxInvoice ADD COLUMN IF NOT EXISTS shipToAddress   VARCHAR(500);
ALTER TABLE taxInvoice ADD COLUMN IF NOT EXISTS shipToGstin     VARCHAR(15);
ALTER TABLE taxInvoice ADD COLUMN IF NOT EXISTS shipToStateCode VARCHAR(2);
