-- Strip result fields from WorkOrderQaEntry (it is now a parameter template only)
ALTER TABLE WorkOrderQaEntry DROP COLUMN IF EXISTS actualValue;
ALTER TABLE WorkOrderQaEntry DROP COLUMN IF EXISTS passed;
ALTER TABLE WorkOrderQaEntry DROP COLUMN IF EXISTS result;
ALTER TABLE WorkOrderQaEntry DROP COLUMN IF EXISTS remarks;
ALTER TABLE WorkOrderQaEntry DROP COLUMN IF EXISTS checkedBy;
ALTER TABLE WorkOrderQaEntry DROP COLUMN IF EXISTS checkedAt;

-- Actual batch results (many per parameter entry)
CREATE TABLE WorkOrderQaResult (
    id BIGSERIAL PRIMARY KEY,
    workOrderQaEntryId BIGINT NOT NULL,
    checkedQuantity NUMERIC(15, 5),
    actualValue NUMERIC(15, 4),
    passed BOOLEAN,
    result VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    remarks VARCHAR(500),
    checkedBy VARCHAR(150),
    checkedAt TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    creationDate TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_woqr_entry FOREIGN KEY (workOrderQaEntryId) REFERENCES WorkOrderQaEntry(id)
);

CREATE INDEX idx_woqr_entry ON WorkOrderQaResult(workOrderQaEntryId);
