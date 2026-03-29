ALTER TABLE routingOperation
    ADD COLUMN laborRoleId       BIGINT,
    ADD COLUMN numberOfOperators INTEGER NOT NULL DEFAULT 1,
    ADD COLUMN machineDetailsId  BIGINT,
    ADD COLUMN costType          VARCHAR(30) NOT NULL DEFAULT 'CALCULATED',
    ADD COLUMN fixedCostPerUnit  NUMERIC(10,2);

ALTER TABLE routingOperation
    ADD CONSTRAINT fk_routing_op_labor_role
        FOREIGN KEY (laborRoleId)
        REFERENCES laborRole(id),
    ADD CONSTRAINT fk_routing_op_machine
        FOREIGN KEY (machineDetailsId)
        REFERENCES machinedetails(id);
