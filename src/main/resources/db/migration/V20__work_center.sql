ALTER TABLE workcenter
    ADD COLUMN department VARCHAR(255);

ALTER TABLE workcenter
    ADD COLUMN location VARCHAR(255);

ALTER TABLE workcenter
    ADD COLUMN maxLoadPercentage INT;

ALTER TABLE workcenter
    ADD COLUMN supervisor VARCHAR(255);

CREATE TABLE workcenter_available_shifts (
    workCenterId INT NOT NULL,
    available_shifts VARCHAR(255),
    list_order INT,
    CONSTRAINT fk_wc_shifts FOREIGN KEY (workCenterId)
        REFERENCES workcenter(id)
        ON DELETE CASCADE
);


ALTER TABLE machineDetails
    ADD COLUMN workCenterId INT;

ALTER TABLE machineDetails
    ADD CONSTRAINT fk_machine_workcenter
        FOREIGN KEY (workCenterId)
        REFERENCES workcenter(id)
        ON DELETE SET NULL;