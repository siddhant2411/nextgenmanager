-- Migration to add columns for Fulfillment Tracking and COGS Calculation

-- Add actualCost to DeliveryNoteItem for Profitability reporting
ALTER TABLE deliverynoteitem ADD COLUMN IF NOT EXISTS actualCost NUMERIC(12, 2);

-- Add tracking columns to InventoryInstance for granular allocation audit
ALTER TABLE inventoryinstance ADD COLUMN IF NOT EXISTS salesOrderItemId BIGINT;
ALTER TABLE inventoryinstance ADD COLUMN IF NOT EXISTS deliveryNoteItemId BIGINT;
ALTER TABLE inventoryinstance ADD COLUMN IF NOT EXISTS inventoryRequestId BIGINT;

-- Add Quality Tracking to Batch and Serial numbers
ALTER TABLE batchnumber ADD COLUMN IF NOT EXISTS qualityStatus VARCHAR(50);
ALTER TABLE batchnumber ADD COLUMN IF NOT EXISTS qualityRemarks VARCHAR(500);

ALTER TABLE serialnumber ADD COLUMN IF NOT EXISTS qualityStatus VARCHAR(50);
ALTER TABLE serialnumber ADD COLUMN IF NOT EXISTS qualityRemarks VARCHAR(500);

-- Create InventoryMovementLog table for genealogy tracking
CREATE TABLE IF NOT EXISTS inventoryMovementLog (
    id BIGINT PRIMARY KEY,
    inventoryInstanceId BIGINT NOT NULL,
    fromLocation VARCHAR(255),
    toLocation VARCHAR(255),
    transactionType VARCHAR(50),
    referenceNumber VARCHAR(100),
    actionBy VARCHAR(100),
    remarks VARCHAR(500),
    timestamp TIMESTAMP WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_movement_instance FOREIGN KEY (inventoryInstanceId) REFERENCES inventoryinstance(id)
);

CREATE SEQUENCE IF NOT EXISTS inventory_movement_log_seq START WITH 1 INCREMENT BY 50;

