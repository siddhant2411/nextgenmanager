ALTER TABLE workOrderProductionTemplate
ADD COLUMN isSequenceValidated  bool,
ADD COLUMN costingMethod  SMALLINT,
ADD COLUMN effectiveFrom  TIMESTAMP,
ADD COLUMN effectiveTo  TIMESTAMP,
ADD COLUMN changeReason  VARCHAR(100),
ADD COLUMN changedBy  VARCHAR(100),
ADD COLUMN versionNumber  INT,
ADD COLUMN isActiveVersion  bool;

