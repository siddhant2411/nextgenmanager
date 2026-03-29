ALTER TABLE routingOperation
    ADD COLUMN productionJobId INT,
    ADD CONSTRAINT fk_routing_op_job
        FOREIGN KEY (productionJobId)
        REFERENCES productionJob(id);