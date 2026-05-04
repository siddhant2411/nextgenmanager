CREATE TABLE WorkOrderQaEntry (
    id BIGSERIAL PRIMARY KEY,
    workOrderOperationId BIGINT NOT NULL,
    bomQaParameterId BIGINT,
    parameterName VARCHAR(255) NOT NULL,
    parameterType VARCHAR(30) NOT NULL,
    minValue NUMERIC(15, 4),
    maxValue NUMERIC(15, 4),
    unit VARCHAR(50),
    critical BOOLEAN NOT NULL DEFAULT FALSE,
    actualValue NUMERIC(15, 4),
    passed BOOLEAN,
    result VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    remarks VARCHAR(500),
    checkedBy VARCHAR(150),
    checkedAt TIMESTAMP,
    creationDate TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updatedDate TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_woqe_operation FOREIGN KEY (workOrderOperationId) REFERENCES WorkOrderOperation(id)
);

CREATE INDEX idx_woqe_operation ON WorkOrderQaEntry(workOrderOperationId);
