-- Exchange rate on quotations, sales orders and tax invoices: rupees per one unit of the
-- document's currency.
--
-- A quotation or order can be written in the customer's currency, but the books, the GST returns
-- and every item price, cost and minimum price are in rupees. Until now the amounts of a dollar
-- order were posted to the ledger as if they were rupees, and its margin compared dollars with
-- rupee costs. The invoice keeps its own copy of the currency and rate, taken from the order when
-- it is raised, so a later edit of the order cannot reach an invoice that has already been posted.
--
-- Every existing document gets a rate of 1, which is right for the rupee documents that make up
-- all of them in practice; a foreign-currency order already saved needs its rate entered by hand.
-- IF NOT EXISTS: a local run with ddl-auto on may already have added the columns from the entities.
ALTER TABLE quotation  ADD COLUMN IF NOT EXISTS exchangeRate NUMERIC(18, 6) NOT NULL DEFAULT 1;
ALTER TABLE salesOrder ADD COLUMN IF NOT EXISTS exchangeRate NUMERIC(18, 6) NOT NULL DEFAULT 1;

ALTER TABLE taxInvoice ADD COLUMN IF NOT EXISTS currency     VARCHAR(20);
ALTER TABLE taxInvoice ADD COLUMN IF NOT EXISTS exchangeRate NUMERIC(18, 6) NOT NULL DEFAULT 1;

-- An invoice raised before this was in its order's currency.
UPDATE taxInvoice ti
SET currency = so.currency
FROM salesOrder so
WHERE ti.salesorder_id = so.id
  AND ti.currency IS NULL;
