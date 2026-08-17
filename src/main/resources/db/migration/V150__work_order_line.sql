-- Multi-item work orders, phase A: introduce workOrderLine as the unit of manufacture.
--
-- A work order becomes a header (number, customer/SO, priority, dates, release) carrying one or
-- more lines, each of which produces ONE finished item from its own BOM + routing + quantity.
-- Until now a work order was hard-wired to a single item, and the item was not even stored: it
-- was re-derived as bom.parentInventoryItem everywhere it was needed.
--
-- This migration is deliberately additive and reversible. Every existing work order is backfilled
-- with exactly ONE line carrying its current header values, so behaviour is unchanged. The old
-- workOrder.bomId / routeId / plannedQuantity columns are left in place and still populated; they
-- are dropped only in V152, once no code reads them.
--
-- id uses GenerationType.IDENTITY (like workOrderMaterial / workOrderOperation), hence BIGSERIAL
-- rather than the INCREMENT BY 50 sequence pattern used by the AUTO-strategy entities.

CREATE TABLE workOrderLine (
    -- Column types deliberately mirror the referenced PKs as they exist in the schema, which are
    -- not uniform: workOrder.id and routing.id are BIGINT while bom.id and
    -- inventoryItem.inventoryItemId are INTEGER.
    id                         BIGSERIAL     NOT NULL PRIMARY KEY,
    workOrderId                BIGINT        NOT NULL,
    lineNumber                 INTEGER       NOT NULL,
    inventoryItemId            INTEGER,
    bomId                      INTEGER,
    routeId                    BIGINT,

    plannedQuantity            NUMERIC(15,5),
    completedQuantity          NUMERIC(15,5) DEFAULT 0,
    scrappedQuantity           NUMERIC(15,5) DEFAULT 0,

    status                     VARCHAR(255),
    salesOrderItemId           BIGINT,
    dueDate                    TIMESTAMP,

    estimatedProductionMinutes NUMERIC(15,2),
    estimatedTotalCost         NUMERIC(15,2),

    splitFromLineId            BIGINT,

    creationDate               TIMESTAMP,
    updatedDate                TIMESTAMP,
    deletedDate                TIMESTAMP,

    CONSTRAINT fk_workOrderLine_workOrder FOREIGN KEY (workOrderId)     REFERENCES workOrder(id),
    CONSTRAINT fk_workOrderLine_item      FOREIGN KEY (inventoryItemId) REFERENCES inventoryItem(inventoryItemId),
    CONSTRAINT fk_workOrderLine_bom       FOREIGN KEY (bomId)           REFERENCES bom(id),
    CONSTRAINT fk_workOrderLine_routing   FOREIGN KEY (routeId)         REFERENCES routing(id),
    CONSTRAINT uq_workOrderLine_wo_line   UNIQUE (workOrderId, lineNumber)
);

CREATE INDEX idx_wol_workorder ON workOrderLine (workOrderId);
CREATE INDEX idx_wol_item      ON workOrderLine (inventoryItemId);

-- Provenance for split-created work orders. Deliberately NOT parentWorkOrderId: that column
-- already means "parent assembly" (WorkOrderSourceType.PARENT_WORK_ORDER) and overloading it
-- would corrupt the assembly tree.
ALTER TABLE workOrder
    ADD COLUMN IF NOT EXISTS splitFromWorkOrderId BIGINT;

-- ── Backfill: exactly one line per existing work order ───────────────────────────────────────
-- Soft-deleted work orders are included (and inherit their deletedDate) so that their children
-- still resolve to a line; excluding them would leave orphaned material/operation rows.
INSERT INTO workOrderLine (
    workOrderId, lineNumber, inventoryItemId, bomId, routeId,
    plannedQuantity, completedQuantity, scrappedQuantity,
    status, dueDate, estimatedProductionMinutes, estimatedTotalCost,
    creationDate, updatedDate, deletedDate
)
SELECT
    wo.id,
    1,
    b.parentInventoryItemId,
    wo.bomId,
    wo.routeId,
    wo.plannedQuantity,
    COALESCE(wo.completedQuantity, 0),
    COALESCE(wo.scrappedQuantity, 0),
    wo.workOrderStatus,
    wo.dueDate,
    wo.estimatedProductionMinutes,
    wo.estimatedTotalCost,
    wo.creationDate,
    wo.updatedDate,
    wo.deletedDate
FROM workOrder wo
LEFT JOIN bom b ON b.id = wo.bomId;

-- ── Re-parent the children onto their line ───────────────────────────────────────────────────
ALTER TABLE workOrderMaterial
    ADD COLUMN IF NOT EXISTS workOrderLineId BIGINT;
ALTER TABLE workOrderOperation
    ADD COLUMN IF NOT EXISTS workOrderLineId BIGINT;

UPDATE workOrderMaterial m
SET workOrderLineId = l.id
FROM workOrderLine l
WHERE l.workOrderId = m.workOrderId
  AND l.lineNumber = 1
  AND m.workOrderLineId IS NULL;

UPDATE workOrderOperation o
SET workOrderLineId = l.id
FROM workOrderLine l
WHERE l.workOrderId = o.workOrderId
  AND l.lineNumber = 1
  AND o.workOrderLineId IS NULL;

ALTER TABLE workOrderMaterial
    ADD CONSTRAINT fk_workOrderMaterial_line FOREIGN KEY (workOrderLineId) REFERENCES workOrderLine(id);
ALTER TABLE workOrderOperation
    ADD CONSTRAINT fk_workOrderOperation_line FOREIGN KEY (workOrderLineId) REFERENCES workOrderLine(id);

CREATE INDEX idx_wom_line ON workOrderMaterial (workOrderLineId);
CREATE INDEX idx_woo_line ON workOrderOperation (workOrderLineId);
