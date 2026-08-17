-- Material uniqueness is per LINE, not per work order.
--
-- Companion to V151, same root cause. V33 added UNIQUE (workOrderId, componentId) when a work
-- order exploded exactly one BOM, so a component could appear only once. On a multi-item work
-- order each line explodes its own BOM, and two BOMs sharing a raw material is entirely normal
-- — a steel bar consumed by both a pump and a flange. The old constraint rejected the second
-- line's insert:
--
--   duplicate key value violates unique constraint "uq_wom_work_order_component"
--   Key (workorderid, componentid)=(9953, 16) already exists.
--
-- Scoping to the line preserves the original guarantee (one row per component within a single
-- BOM explosion) while letting lines share components.
--
-- No de-duplication is needed before adding the new key: every existing work order has exactly
-- one line (V150 backfill), so uniqueness on (workOrderId, componentId) already implies
-- uniqueness on (workOrderLineId, componentId). workOrderLineId was pinned NOT NULL in V151, so
-- no row can slip past the constraint via a NULL.

ALTER TABLE workOrderMaterial
    DROP CONSTRAINT IF EXISTS uq_wom_work_order_component;

ALTER TABLE workOrderMaterial
    ADD CONSTRAINT uq_wom_line_component UNIQUE (workOrderLineId, componentId);
