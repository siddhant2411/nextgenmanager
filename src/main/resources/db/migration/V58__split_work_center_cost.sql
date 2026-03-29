ALTER TABLE workCenter
    RENAME COLUMN costPerHour TO machineCostPerHour;

ALTER TABLE workCenter
    ADD COLUMN overheadPercentage NUMERIC(5,2) NOT NULL DEFAULT 0;
