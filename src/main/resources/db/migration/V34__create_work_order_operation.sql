CREATE TABLE workorderoperation (
    id BIGSERIAL PRIMARY KEY,

    workorderid BIGINT NOT NULL,
    routingoperationid BIGINT,

    sequence INTEGER NOT NULL,
    operationname VARCHAR(255) NOT NULL,

    workcenterid BIGINT,

    plannedquantity   NUMERIC(15,5) NOT NULL,
    completedquantity NUMERIC(15,5) NOT NULL DEFAULT 0,
    scrappedquantity  NUMERIC(15,5) NOT NULL DEFAULT 0,

    plannedstartdate TIMESTAMP,
    plannedenddate   TIMESTAMP,
    actualstartdate  TIMESTAMP,
    actualenddate    TIMESTAMP,

    status VARCHAR(50) NOT NULL,

    ismilestone BOOLEAN NOT NULL DEFAULT FALSE,
    allowovercompletion BOOLEAN NOT NULL DEFAULT FALSE,

    creationdate TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updateddate  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleteddate  TIMESTAMP,

    CONSTRAINT fk_woo_work_order
        FOREIGN KEY (workorderid)
        REFERENCES workorder(id),

    CONSTRAINT fk_woo_routing_operation
        FOREIGN KEY (routingoperationid)
        REFERENCES routingoperation(id),

    CONSTRAINT fk_woo_work_center
        FOREIGN KEY (workcenterid)
        REFERENCES workcenter(id),

    CONSTRAINT uq_woo_work_order_sequence
        UNIQUE (workorderid, sequence)
);
