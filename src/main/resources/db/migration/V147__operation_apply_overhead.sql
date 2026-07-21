-- Opt-in work-center overhead on piece-rate (RATE_TIMES_QTY) operations.
--
-- Historically piece-rate cost was a flat rate × quantity with no overhead applied, on the
-- assumption the negotiated rate was all-in. That silently ignored the selected work center's
-- overhead%. This flag lets an operation explicitly load the work center overhead on top of the
-- piece-rate subtotal. It defaults to FALSE so existing BOM/work-order costs are unchanged; turn
-- it on only where the piece rate is a direct (overhead-exclusive) rate.
ALTER TABLE routingOperation
    ADD COLUMN IF NOT EXISTS applyOverhead BOOLEAN NOT NULL DEFAULT FALSE;
