ALTER TABLE workorderproductiontemplate
    DROP COLUMN IF EXISTS routingversionnumber,
    DROP COLUMN IF EXISTS routingversion,
    DROP COLUMN IF EXISTS routingstatus,
    DROP COLUMN IF EXISTS issequencevalidated;


CREATE TABLE routing (
    id BIGSERIAL PRIMARY KEY,

    -- One routing per BOM (mandatory + unique)
    bomId INT NOT NULL UNIQUE,

    status SMALLINT NOT NULL DEFAULT 0,

    createdBy VARCHAR(100),
    creationDate TIMESTAMP DEFAULT NOW(),
    updatedDate TIMESTAMP,
    deletedDate TIMESTAMP,

    CONSTRAINT fk_routing_wopt
        FOREIGN KEY (bomId)
        REFERENCES bom(id)
        ON DELETE CASCADE
);


CREATE TABLE routingOperation (
    id BIGSERIAL PRIMARY KEY,

    routingId BIGINT NOT NULL,
    sequenceNumber INT,
    name VARCHAR(255) NOT NULL,

    workCenterId BIGINT,
    setupTime NUMERIC(10,2),
    runTime NUMERIC(10,2),

    inspection BOOLEAN DEFAULT FALSE,
    notes TEXT,

    opOrder INT,

    CONSTRAINT fk_routing_operation_routing
        FOREIGN KEY (routingId)
        REFERENCES routing(id)
        ON DELETE CASCADE,

    CONSTRAINT fk_routing_operation_workcenter
        FOREIGN KEY (workCenterId)
        REFERENCES workCenter(id)
);


CREATE INDEX idx_routing_status ON routing(status);
CREATE INDEX idx_routing_operation_routing ON routingOperation(routingId);