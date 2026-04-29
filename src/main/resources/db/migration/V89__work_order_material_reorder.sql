CREATE TABLE WorkOrderMaterialReorder (
    id                   BIGSERIAL PRIMARY KEY,
    workOrderMaterialId  BIGINT NOT NULL REFERENCES workordermaterial(id),
    inventoryRequestId   BIGINT,
    requestedQuantity    NUMERIC(15, 5) NOT NULL,
    shortfallQuantity    NUMERIC(15, 5) NOT NULL DEFAULT 0,
    remarks              TEXT,
    createdBy            VARCHAR(255),
    createdDate          TIMESTAMP DEFAULT NOW()
);

CREATE INDEX idx_womr_material ON WorkOrderMaterialReorder (workOrderMaterialId);
