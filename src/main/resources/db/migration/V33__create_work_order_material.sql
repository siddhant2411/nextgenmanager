CREATE TABLE work_order_material (
    id BIGSERIAL PRIMARY KEY,

    workOrderId BIGINT NOT NULL,
    componentId BIGINT NOT NULL,

    requiredQuantity NUMERIC(15,5) NOT NULL,
    issuedQuantity   NUMERIC(15,5) NOT NULL DEFAULT 0,
    scrappedQuantity NUMERIC(15,5) NOT NULL DEFAULT 0,

    issueStatus VARCHAR(50) NOT NULL,

    backflush BOOLEAN NOT NULL DEFAULT FALSE,

    creation_date TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_date  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_date  TIMESTAMP,

    CONSTRAINT fk_wom_work_order
        FOREIGN KEY (workOrderId)
        REFERENCES workOrder(id),

    CONSTRAINT fk_wom_component
        FOREIGN KEY (componentId)
        REFERENCES inventoryitem(inventoryItemId),

    CONSTRAINT uq_wom_work_order_component
        UNIQUE (workOrderId, componentId)
);
