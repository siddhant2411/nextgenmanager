-- Operation sequence is unique per LINE, not per work order.
--
-- V34 added UNIQUE (workOrderId, sequence) back when a work order made exactly one item from
-- exactly one routing, so sequence numbers could not collide. A multi-item work order runs one
-- routing per line, and routings routinely share sequence numbers — two lines each having an
-- operation 10 is normal, not an error. The old constraint rejects the second line's insert:
--
--   duplicate key value violates unique constraint "uq_woo_work_order_sequence"
--   Key (workorderid, sequence)=(7452, 10) already exists.
--
-- Widening the key to include the line preserves the original guarantee (no duplicate sequence
-- within one routing run) while allowing lines to sequence independently.

ALTER TABLE workOrderOperation
    DROP CONSTRAINT IF EXISTS uq_woo_work_order_sequence;

-- Any pre-V150 rows already carry a workOrderLineId from the V150 backfill, so no row has a
-- NULL line here. NULLs would in any case be excluded from a UNIQUE constraint in Postgres,
-- which would silently weaken the guarantee — so the column is pinned NOT NULL first.
UPDATE workOrderOperation o
SET workOrderLineId = l.id
FROM workOrderLine l
WHERE l.workOrderId = o.workOrderId
  AND l.lineNumber = 1
  AND o.workOrderLineId IS NULL;

ALTER TABLE workOrderOperation
    ALTER COLUMN workOrderLineId SET NOT NULL;

ALTER TABLE workOrderOperation
    ADD CONSTRAINT uq_woo_line_sequence UNIQUE (workOrderLineId, sequence);

-- Same story for materials: they hang off a line and every row was backfilled by V150.
UPDATE workOrderMaterial m
SET workOrderLineId = l.id
FROM workOrderLine l
WHERE l.workOrderId = m.workOrderId
  AND l.lineNumber = 1
  AND m.workOrderLineId IS NULL;

ALTER TABLE workOrderMaterial
    ALTER COLUMN workOrderLineId SET NOT NULL;
