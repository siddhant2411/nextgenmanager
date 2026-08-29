-- purchaseOrderItem.unitPrice has been storing 2 decimals while the entity declared 4.
--
-- The column came from V1__baseline as numeric(38,2). When V101 built out the purchase module it
-- added every other money column at the scale PurchaseOrderItem declares -- discountAmount,
-- taxableValue, the four tax columns -- but unitPrice already existed, so it was never altered.
-- Hibernate does not reconcile an existing column against @Column(precision, scale), so the
-- annotation has been describing a database that does not behave that way:
--
--     @Column(precision = 14, scale = 4)
--     private BigDecimal unitPrice;
--
-- Anything with more than two decimals is silently rounded on write. It surfaced loading PEC's
-- 2026 purchase register, where the rate is quoted per kilogram and the per-piece price is a rate
-- times a weight: 67/kg x 11.875 kg = 795.625, stored as 795.63. Small money -- twelve paise on
-- the worst line of 372 -- but the value read back never equals the value sent, so any importer
-- or agent that compares the two sees drift that is not there and rewrites the record on every
-- run, which then hides a real edit when one arrives.
--
-- Precision narrows from 38 to 14 to match the entity exactly. Ten digits ahead of the decimal
-- point is a unit price of ten billion rupees; the largest on file is 243,000.

ALTER TABLE purchaseorderitem
    ALTER COLUMN unitprice TYPE NUMERIC(14, 4);
