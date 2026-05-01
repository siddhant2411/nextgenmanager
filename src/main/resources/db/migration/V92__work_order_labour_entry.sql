CREATE TABLE WorkOrderLabourEntry (
    id               BIGSERIAL PRIMARY KEY,
    workOrderOperationId BIGINT NOT NULL REFERENCES WorkOrderOperation(id),
    operatorName     VARCHAR(100),
    laborRoleId      BIGINT REFERENCES laborRole(id),
    laborType        VARCHAR(20) NOT NULL DEFAULT 'RUN',
    startTime        TIMESTAMP,
    endTime          TIMESTAMP,
    durationMinutes  NUMERIC(10, 2),
    costRatePerHour  NUMERIC(10, 2),
    totalCost        NUMERIC(15, 2),
    remarks          VARCHAR(500),
    creationDate     TIMESTAMP DEFAULT NOW(),
    updatedDate      TIMESTAMP,
    deletedDate      TIMESTAMP
);

CREATE INDEX idx_wo_labour_operation ON WorkOrderLabourEntry(workOrderOperationId);
