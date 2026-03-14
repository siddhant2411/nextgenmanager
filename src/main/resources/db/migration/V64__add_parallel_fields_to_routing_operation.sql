-- V64: Add parallel operation support fields to routingOperation.
-- allowParallel: marks this operation as eligible for concurrent execution.
-- parallelPath:  groups operations into named parallel execution streams.

ALTER TABLE routingOperation
    ADD COLUMN allowParallel BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN parallelPath  VARCHAR(50);
