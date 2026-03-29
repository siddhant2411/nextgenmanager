CREATE TABLE workorderhistory (
    id BIGSERIAL PRIMARY KEY,
    workorderid BIGINT NOT NULL,
    eventtype VARCHAR(50) NOT NULL,
    fieldname VARCHAR(100),
    oldvalue TEXT,
    newvalue TEXT,
    performedby VARCHAR(100),
    performedat TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    remarks TEXT,

    CONSTRAINT fk_woh_work_order
        FOREIGN KEY (workorderid)
        REFERENCES workorder(id)
);