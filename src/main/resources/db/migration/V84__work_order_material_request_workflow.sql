-- V84: Work Order → Material Request workflow
-- Adds new fields to inventoryRequest for MR approval tracking
-- Migrates legacy RELEASED work orders to MATERIAL_PENDING

ALTER TABLE inventoryRequest
    ADD COLUMN IF NOT EXISTS requestedQuantity DECIMAL(15, 5),
    ADD COLUMN IF NOT EXISTS approvedQuantity  DECIMAL(15, 5),
    ADD COLUMN IF NOT EXISTS rejectionReason   VARCHAR(500),
    ADD COLUMN IF NOT EXISTS approvedBy        VARCHAR(100),
    ADD COLUMN IF NOT EXISTS approvedDate      TIMESTAMP;

-- Migrate any in-flight RELEASED work orders to the new MATERIAL_PENDING status.
-- These WOs had stock reserved under the old flow; they now need store approval
-- under the new flow. Treating them as MATERIAL_PENDING surfaces them in the
-- Stores approval queue so the store keeper can confirm or adjust.
UPDATE workorder
SET workOrderStatus = 'MATERIAL_PENDING'
WHERE workOrderStatus = 'RELEASED';
