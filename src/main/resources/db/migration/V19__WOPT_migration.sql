CREATE TABLE workCenter (
    id SERIAL PRIMARY KEY,
    centerCode VARCHAR(50) NOT NULL UNIQUE,
    centerName VARCHAR(100) NOT NULL,
    description VARCHAR(500),
    costPerHour NUMERIC(10,2),
    availableHoursPerDay NUMERIC(10,2),
    workCenterStatus smallint NOT NULL DEFAULT 0,
    creationDate TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updatedDate TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deletedDate TIMESTAMP
);

ALTER TABLE MachineDetails
ADD COLUMN machineCode VARCHAR(100),
ADD COLUMN work_center_id INT4 REFERENCES workCenter(id),
ADD COLUMN costPerHour NUMERIC(10,2),
ADD COLUMN availableHoursPerDay NUMERIC(10,2),
ADD COLUMN machineStatus smallint NOT NULL DEFAULT 0;


ALTER TABLE productionJob
ADD COLUMN defaultSetupTime NUMERIC(10,2),
ADD COLUMN defaultRunTimePerUnit NUMERIC(10,2),
ADD COLUMN category VARCHAR(100);

ALTER TABLE workOrderJobList
    ADD COLUMN operationNumber INTEGER,
    ADD COLUMN workCenterId INT REFERENCES workCenter(id),
    ADD COLUMN setupTime NUMERIC(10,2),
    ADD COLUMN runTimePerUnit NUMERIC(10,2),
    ADD COLUMN labourCost NUMERIC(10,2),
    ADD COLUMN overheadCost NUMERIC(10,2),
    ADD COLUMN operationDescription VARCHAR(100),
    ADD COLUMN isParallelOperation BOOLEAN DEFAULT FALSE;

ALTER TABLE workOrderJobList
    RENAME COLUMN production_job TO production_job_id;

ALTER TABLE workOrderJobList
    RENAME COLUMN workOrderProductionTemplate_job_list_id TO wopt_id;


ALTER TABLE workOrderProductionTemplate
    ADD COLUMN totalSetupTime NUMERIC(10,2),
    ADD COLUMN routingVersionNumber INT,
    ADD COLUMN routingVersion VARCHAR(10),
    ADD COLUMN routingStatus smallint NOT NULL DEFAULT 0,
    ADD COLUMN totalRunTime NUMERIC(10,2),
    ADD COLUMN default_work_center_id INT REFERENCES workCenter(id);





