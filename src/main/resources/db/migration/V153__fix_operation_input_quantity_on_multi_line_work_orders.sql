-- Operation input quantity is per LINE, not per work order.
--
-- Third in the V150-V152 series, same root cause. A work order's plannedQuantity is the SUM
-- across its lines, but availableInputQuantity was seeded from that total in two places:
--
--   * at release, every line's first operation got the work order's total; and
--   * on unlock, a dependent operation was SET to the work order's total rather than raised by
--     what its upstream operation actually forwarded.
--
-- On a single-line work order the line total and the work order total are equal, so this was
-- invisible until multi-item work orders arrived. On a two-line order of 1 + 2, every operation
-- reported 3 units of input — the shop floor was told it could run three units of a line that
-- only makes one.
--
-- Both write sites now use the operation's own planned quantity, which explodeLine sets from its
-- line. This repairs the rows already written by the old code.
--
-- Scope and safety:
--   * Only multi-line work orders are touched. Single-line orders were never over-seeded (their
--     line total IS the work order total), so they are excluded rather than relied upon to be
--     no-ops.
--   * Only rows where input EXCEEDS the operation's own planned quantity are touched. An
--     operation part-way through a sequential chain holds whatever upstream forwarded so far,
--     which is at or below its planned quantity — those rows are already correct and are left
--     alone.
--   * Operations allowing over-completion are excluded: for them an input above the planned
--     quantity is legitimate, not the seeding bug.
--   * COMPLETED and CANCELLED operations are left as they are. Their input is history at this
--     point, and rewriting it would misrepresent what the floor actually had available.

UPDATE workOrderOperation op
SET availableInputQuantity = op.plannedQuantity
FROM workOrder wo
WHERE op.workOrderId = wo.id
  AND op.deletedDate IS NULL
  AND op.availableInputQuantity > op.plannedQuantity
  AND COALESCE(op.allowOverCompletion, FALSE) = FALSE
  AND op.status NOT IN ('COMPLETED', 'CANCELLED')
  AND wo.id IN (
      SELECT l.workOrderId
      FROM workOrderLine l
      WHERE l.deletedDate IS NULL
      GROUP BY l.workOrderId
      HAVING COUNT(*) > 1
  );
