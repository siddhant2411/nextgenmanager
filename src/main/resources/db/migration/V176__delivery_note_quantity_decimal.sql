-- Delivery note quantities become decimal.
--
-- quantityDelivered has been an integer since the baseline, while orders, picks, stock and packing
-- all carry four decimal places. A pick of 2.5 kg therefore could not be shipped: phase I chose to
-- refuse it rather than truncate it to 2 and lose half a kilo between the shelf and the invoice.
-- Refusing was the right stopgap and the wrong end state — anything sold by weight or length could
-- be ordered, picked and boxed, and then never dispatched.
--
-- Existing rows are whole numbers and convert exactly.

ALTER TABLE deliverynoteitem
    ALTER COLUMN quantitydelivered TYPE NUMERIC(18,4) USING quantitydelivered::NUMERIC(18,4);

COMMENT ON COLUMN deliverynoteitem.quantitydelivered IS
    'Decimal since V176, to the same four places as the order, the pick and the stock it consumes.';
