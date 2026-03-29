-- Phase 1a: Link BOM positions to specific routing operations
-- NULL = "consume at work order start" (backward compatible with all existing rows)
-- ON DELETE SET NULL ensures orphaned references are cleared automatically when
-- a routing is rebuilt (createOrUpdateRouting clears + recreates all operations)

ALTER TABLE bomPosition
    ADD COLUMN routingOperationId BIGINT,
    ADD CONSTRAINT fk_bomposition_routing_operation
        FOREIGN KEY (routingOperationId)
        REFERENCES routingOperation(id)
        ON DELETE SET NULL;
