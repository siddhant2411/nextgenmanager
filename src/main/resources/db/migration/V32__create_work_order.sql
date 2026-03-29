CREATE TABLE workorder (
    id BIGSERIAL PRIMARY KEY,

    workordernumber VARCHAR(100) NOT NULL,

    salesorderid BIGINT,
    parentworkorderid BIGINT,

    workorderstatus VARCHAR(50) NOT NULL,
    sourcetype VARCHAR(50),

    plannedquantity   NUMERIC(15,5),
    completedquantity NUMERIC(15,5) DEFAULT 0,
    scrappedquantity  NUMERIC(15,5) DEFAULT 0,

    bomid BIGINT NOT NULL,
    routeid BIGINT NOT NULL,

    workcenterid BIGINT,

    remarks TEXT,

    duedate DATE,
    plannedstartdate TIMESTAMP,
    plannedenddate   TIMESTAMP,
    actualstartdate  TIMESTAMP,
    actualenddate    TIMESTAMP,

    creationdate TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updateddate  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleteddate  TIMESTAMP,

    CONSTRAINT uq_work_order_number
        UNIQUE (workordernumber),

    CONSTRAINT fk_work_order_sales_order
        FOREIGN KEY (salesorderid)
        REFERENCES salesorder(id),

    CONSTRAINT fk_work_order_parent
        FOREIGN KEY (parentworkorderid)
        REFERENCES workorder(id),

    CONSTRAINT fk_work_order_bom
        FOREIGN KEY (bomid)
        REFERENCES bom(id),

    CONSTRAINT fk_work_order_routing
        FOREIGN KEY (routeid)
        REFERENCES routing(id),

    CONSTRAINT fk_work_order_work_center
        FOREIGN KEY (workcenterid)
        REFERENCES workcenter(id)
);
