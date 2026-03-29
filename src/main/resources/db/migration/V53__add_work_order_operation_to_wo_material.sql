-- Phase 1b: Link Work Order materials to specific Work Order operations
-- Snapshot of bomPosition.routingOperation at WO creation time
-- NULL = "not linked to any specific operation" (backward compatible)
-- ON DELETE SET NULL for safety (WOs use soft deletes, but belt-and-suspenders)

ALTER TABLE WorkOrderMaterial
    ADD COLUMN workOrderOperationId BIGINT,
    ADD CONSTRAINT fk_wo_material_wo_operation
        FOREIGN KEY (workOrderOperationId)
        REFERENCES WorkOrderOperation(id)
        ON DELETE SET NULL;
