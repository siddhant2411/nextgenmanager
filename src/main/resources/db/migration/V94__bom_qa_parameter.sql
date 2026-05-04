CREATE TABLE BomQaParameter (
    id BIGSERIAL PRIMARY KEY,
    routingOperationId BIGINT NOT NULL,
    name VARCHAR(255) NOT NULL,
    parameterType VARCHAR(30) NOT NULL,
    minValue NUMERIC(15, 4),
    maxValue NUMERIC(15, 4),
    unit VARCHAR(50),
    critical BOOLEAN NOT NULL DEFAULT FALSE,
    creationDate TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updatedDate TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deletedDate TIMESTAMP,
    CONSTRAINT fk_bom_qa_param_routing_op FOREIGN KEY (routingOperationId) REFERENCES routingOperation(id)
);

CREATE INDEX idx_bom_qa_param_routing ON BomQaParameter(routingOperationId);
