-- V65: Add parallel operation tracking fields to WorkOrderOperation.
-- parallelPath:           copied from RoutingOperation on WO release.
-- dependencyResolvedDate: timestamp when all blocking dependencies completed.
--
-- WorkOrderOperationDependency: join table tracking which WO operations must
-- complete before a given operation can start (runtime copy of routing dependencies).

ALTER TABLE WorkOrderOperation
    ADD COLUMN parallelPath            VARCHAR(50),
    ADD COLUMN dependencyResolvedDate  TIMESTAMP;

CREATE TABLE WorkOrderOperationDependency (
    workOrderOperationId  BIGINT NOT NULL,
    dependsOnOperationId  BIGINT NOT NULL,

    CONSTRAINT pk_wo_op_dep
        PRIMARY KEY (workOrderOperationId, dependsOnOperationId),

    CONSTRAINT fk_wo_op_dep_operation
        FOREIGN KEY (workOrderOperationId)
        REFERENCES WorkOrderOperation(id)
        ON DELETE CASCADE,

    CONSTRAINT fk_wo_op_dep_depends_on
        FOREIGN KEY (dependsOnOperationId)
        REFERENCES WorkOrderOperation(id)
        ON DELETE CASCADE
);

CREATE INDEX idx_wo_op_dep_op   ON WorkOrderOperationDependency(workOrderOperationId);
CREATE INDEX idx_wo_op_dep_deps ON WorkOrderOperationDependency(dependsOnOperationId);

-- Also add WAITING_FOR_DEPENDENCY to the OperationStatus check constraint if one exists.
-- (PostgreSQL enums are stored as VARCHAR — no constraint change needed.)
