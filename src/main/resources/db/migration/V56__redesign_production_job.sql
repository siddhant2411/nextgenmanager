-- Add new columns
ALTER TABLE productionjob
    ADD COLUMN jobCode VARCHAR(50),
    ADD COLUMN active  BOOLEAN NOT NULL DEFAULT TRUE;

-- Populate jobCode from existing jobName as placeholder
UPDATE productionjob SET jobCode = LEFT(REPLACE(jobName, ' ', '-'), 50);

-- Make jobCode NOT NULL and UNIQUE
ALTER TABLE productionjob
    ALTER COLUMN jobCode SET NOT NULL,
    ADD CONSTRAINT uq_production_job_code UNIQUE (jobCode);

-- Drop foreign keys
ALTER TABLE productionjob DROP CONSTRAINT IF EXISTS fkj8e4ko4sdi5trxvryaimnewpk;  -- machine_details_id (from V1 baseline)
ALTER TABLE productionjob DROP CONSTRAINT IF EXISTS fk_production_job_workcenter;  -- workCenterId (from V23)

-- Drop removed columns
ALTER TABLE productionjob
    DROP COLUMN IF EXISTS machine_details_id,
    DROP COLUMN IF EXISTS workCenterId,
    DROP COLUMN IF EXISTS costPerHour,
    DROP COLUMN IF EXISTS roleRequired,
    DROP COLUMN IF EXISTS category,
    DROP COLUMN IF EXISTS isDeleted;
