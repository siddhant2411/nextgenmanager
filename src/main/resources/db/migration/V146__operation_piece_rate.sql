-- Piece-rate (RATE_TIMES_QTY) operation costing: cost = rate-per-each × quantity.
--
-- The rate is a shared standard stored once on productionJob (so a rate change re-costs every
-- referencing operation), mirroring laborRole.costPerHour for hourly work. The per-BOM quantity
-- (holes / kg / tests) lives on the routing operation. An optional per-operation costRate override
-- lets a single operation deviate from the standard rate without touching the catalog.

-- Shared standard piece-rate + display unit on the operation catalog.
ALTER TABLE productionJob
    ADD COLUMN IF NOT EXISTS defaultPieceRate NUMERIC(12,4);
ALTER TABLE productionJob
    ADD COLUMN IF NOT EXISTS pieceUnit        VARCHAR(30);

-- Per-BOM quantity + optional rate override on the routing operation.
ALTER TABLE routingOperation
    ADD COLUMN IF NOT EXISTS costQuantity NUMERIC(12,4);
ALTER TABLE routingOperation
    ADD COLUMN IF NOT EXISTS costRate     NUMERIC(12,4);

