-- Phase F step 2 — the four free-text warehouse columns become foreign keys.
--
-- inventoryledger, goodsreceiptnote, batchnumber and serialnumber each carried a VARCHAR
-- "warehouse" that nothing constrained: two spellings of the same store were two warehouses,
-- and no query could join stock to a place. Each becomes a NOT NULL reference to the master
-- introduced in V163, and the string is dropped.
--
-- Deliberately one-way. Timing is why it is safe to do now: the four tables are empty following
-- the 2026-08-27 rebuild, so there is nothing to lose in translation. Run against populated
-- books this would be a mapping exercise, not an ALTER.
--
-- The backfill still tries to honour any string that happens to match a warehouse code before
-- falling back to MAIN, so it stays correct if a row appears between writing this and running it.

-- inventoryledger ------------------------------------------------------------

ALTER TABLE inventoryledger ADD COLUMN warehouse_id BIGINT;

UPDATE inventoryledger t SET warehouse_id = COALESCE(
    (SELECT w.id FROM warehouse w
      WHERE UPPER(w.code) = UPPER(TRIM(t.warehouse)) AND w.deletedDate IS NULL),
    (SELECT w.id FROM warehouse w WHERE w.code = 'MAIN'));

ALTER TABLE inventoryledger ALTER COLUMN warehouse_id SET NOT NULL;
ALTER TABLE inventoryledger ADD CONSTRAINT fk_inventoryledger_warehouse
    FOREIGN KEY (warehouse_id) REFERENCES warehouse (id);
ALTER TABLE inventoryledger DROP COLUMN warehouse;

CREATE INDEX idx_inventoryledger_warehouse ON inventoryledger (warehouse_id);

COMMENT ON COLUMN inventoryledger.warehouse_id IS
    'Where the movement happened. NOT NULL: every movement occurs somewhere, and stock-by-place
     reporting silently under-reports the moment a null is allowed.';

-- goodsreceiptnote -----------------------------------------------------------

ALTER TABLE goodsreceiptnote ADD COLUMN warehouse_id BIGINT;

UPDATE goodsreceiptnote t SET warehouse_id = COALESCE(
    (SELECT w.id FROM warehouse w
      WHERE UPPER(w.code) = UPPER(TRIM(t.warehouse)) AND w.deletedDate IS NULL),
    (SELECT w.id FROM warehouse w WHERE w.code = 'MAIN'));

ALTER TABLE goodsreceiptnote ALTER COLUMN warehouse_id SET NOT NULL;
ALTER TABLE goodsreceiptnote ADD CONSTRAINT fk_goodsreceiptnote_warehouse
    FOREIGN KEY (warehouse_id) REFERENCES warehouse (id);
ALTER TABLE goodsreceiptnote DROP COLUMN warehouse;

CREATE INDEX idx_goodsreceiptnote_warehouse ON goodsreceiptnote (warehouse_id);

COMMENT ON COLUMN goodsreceiptnote.warehouse_id IS
    'Receiving warehouse. Phase H will route the rejected quantity to QUARANTINE instead of
     letting it disappear, which needs a real reference here rather than a typed-in name.';

-- batchnumber ----------------------------------------------------------------

ALTER TABLE batchnumber ADD COLUMN warehouse_id BIGINT;

UPDATE batchnumber t SET warehouse_id = COALESCE(
    (SELECT w.id FROM warehouse w
      WHERE UPPER(w.code) = UPPER(TRIM(t.warehouse)) AND w.deletedDate IS NULL),
    (SELECT w.id FROM warehouse w WHERE w.code = 'MAIN'));

ALTER TABLE batchnumber ALTER COLUMN warehouse_id SET NOT NULL;
ALTER TABLE batchnumber ADD CONSTRAINT fk_batchnumber_warehouse
    FOREIGN KEY (warehouse_id) REFERENCES warehouse (id);
ALTER TABLE batchnumber DROP COLUMN warehouse;

CREATE INDEX idx_batchnumber_warehouse ON batchnumber (warehouse_id);

-- serialnumber ---------------------------------------------------------------

ALTER TABLE serialnumber ADD COLUMN warehouse_id BIGINT;

UPDATE serialnumber t SET warehouse_id = COALESCE(
    (SELECT w.id FROM warehouse w
      WHERE UPPER(w.code) = UPPER(TRIM(t.warehouse)) AND w.deletedDate IS NULL),
    (SELECT w.id FROM warehouse w WHERE w.code = 'MAIN'));

ALTER TABLE serialnumber ALTER COLUMN warehouse_id SET NOT NULL;
ALTER TABLE serialnumber ADD CONSTRAINT fk_serialnumber_warehouse
    FOREIGN KEY (warehouse_id) REFERENCES warehouse (id);
ALTER TABLE serialnumber DROP COLUMN warehouse;

CREATE INDEX idx_serialnumber_warehouse ON serialnumber (warehouse_id);

-- Left as free text on purpose: debitnoteitem.warehouseFrom and salescreditnoteitem.warehouseTo
-- are document line fields describing where returned goods came from or went to. They are
-- resolved to a real warehouse when the movement is written to the ledger, so the constrained
-- reference lives on the movement rather than on the paperwork. Converting them is its own step.
