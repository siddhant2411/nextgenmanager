-- V77: Add inventoryRequestId column to WorkOrderMaterial table
ALTER TABLE workordermaterial ADD COLUMN inventoryRequestId BIGINT;

-- Add foreign key constraint to inventoryrequest table (which exists as public.inventoryrequest)
ALTER TABLE workordermaterial
    ADD CONSTRAINT fk_wom_inventory_request
    FOREIGN KEY (inventoryRequestId) REFERENCES inventoryrequest(id);
