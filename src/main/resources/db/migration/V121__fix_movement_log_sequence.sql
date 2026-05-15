-- Fix sequence naming for InventoryMovementLog to match Hibernate default convention
-- Hibernate expects {EntityName}_SEQ or lowercased version if not specified

CREATE SEQUENCE IF NOT EXISTS inventoryMovementLog_SEQ START WITH 1 INCREMENT BY 50;

-- Also create lowercase version just in case of environment differences
CREATE SEQUENCE IF NOT EXISTS inventorymovementlog_seq START WITH 1 INCREMENT BY 50;
