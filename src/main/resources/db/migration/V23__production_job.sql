ALTER TABLE ProductionJob
ADD COLUMN workCenterId INT REFERENCES workCenter(id);

ALTER TABLE ProductionJob
ADD CONSTRAINT fk_production_job_workcenter
        FOREIGN KEY (workCenterId)
        REFERENCES workcenter(id)
        ON DELETE SET NULL;