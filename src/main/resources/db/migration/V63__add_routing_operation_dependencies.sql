-- V63: Add explicit dependency declarations between routing operations.
-- This enables defining parallel and sequential operation relationships
-- independently of sequence number ordering.

CREATE TABLE RoutingOperationDependency (
    id                          BIGSERIAL PRIMARY KEY,
    routingOperationId          BIGINT NOT NULL,
    dependsOnRoutingOperationId BIGINT NOT NULL,
    dependencyType              VARCHAR(20) NOT NULL DEFAULT 'SEQUENTIAL',
    isRequired                  BOOLEAN NOT NULL DEFAULT TRUE,

    CONSTRAINT fk_rod_routing_op
        FOREIGN KEY (routingOperationId)
        REFERENCES routingOperation(id)
        ON DELETE CASCADE,

    CONSTRAINT fk_rod_depends_on_op
        FOREIGN KEY (dependsOnRoutingOperationId)
        REFERENCES routingOperation(id)
        ON DELETE CASCADE,

    CONSTRAINT uq_rod_pair
        UNIQUE (routingOperationId, dependsOnRoutingOperationId)
);

CREATE INDEX idx_rod_routing_op ON RoutingOperationDependency(routingOperationId);
CREATE INDEX idx_rod_depends_on ON RoutingOperationDependency(dependsOnRoutingOperationId);
