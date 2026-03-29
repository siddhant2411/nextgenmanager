-- Align machinedetails schema with updated MachineDetails entity:
-- 1) workCenter linkage uses workCenterId and is required
-- 2) costPerHour is required with default 0
-- 3) machineStatus stored as STRING values instead of ordinal

-- Ensure target FK column exists (historical schemas may only have work_center_id)
ALTER TABLE machinedetails
    ADD COLUMN IF NOT EXISTS workcenterid INT;

-- Migrate values from legacy snake_case column if present
DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_name = 'machinedetails'
          AND column_name = 'work_center_id'
    ) THEN
        UPDATE machinedetails
        SET workcenterid = COALESCE(workcenterid, work_center_id)
        WHERE workcenterid IS NULL;
    END IF;
END $$;

-- Rebuild FK to match required relationship semantics
ALTER TABLE machinedetails DROP CONSTRAINT IF EXISTS fk_machine_workcenter;
ALTER TABLE machinedetails DROP CONSTRAINT IF EXISTS machinedetails_workcenterid_fkey;

ALTER TABLE machinedetails
    ADD CONSTRAINT fk_machinedetails_workcenter
    FOREIGN KEY (workcenterid)
    REFERENCES workcenter(id)
    ON DELETE RESTRICT;

-- Enforce non-null workCenterId after migration from legacy column
DO $$
DECLARE null_count INT;
BEGIN
    SELECT COUNT(*) INTO null_count
    FROM machinedetails
    WHERE workcenterid IS NULL;

    IF null_count > 0 THEN
        RAISE EXCEPTION 'V49 blocked: machinedetails.workCenterId still NULL for % row(s). Backfill those rows before rerun.', null_count;
    END IF;
END $$;

ALTER TABLE machinedetails
    ALTER COLUMN workcenterid SET NOT NULL;

-- Remove legacy column if present
ALTER TABLE machinedetails
    DROP COLUMN IF EXISTS work_center_id;

-- costPerHour must be non-null with default 0
UPDATE machinedetails
SET costperhour = 0
WHERE costperhour IS NULL;

ALTER TABLE machinedetails
    ALTER COLUMN costperhour SET DEFAULT 0;

ALTER TABLE machinedetails
    ALTER COLUMN costperhour SET NOT NULL;

-- Convert machineStatus from ordinal smallint to enum string values
DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_name = 'machinedetails'
          AND column_name = 'machinestatus'
          AND data_type IN ('smallint', 'integer', 'bigint')
    ) THEN
        ALTER TABLE machinedetails ADD COLUMN machinestatus_new VARCHAR(30);

        UPDATE machinedetails
        SET machinestatus_new = CASE machinestatus
            WHEN 0 THEN 'ACTIVE'
            WHEN 1 THEN 'UNDER_MAINTENANCE'
            WHEN 2 THEN 'BREAKDOWN'
            WHEN 3 THEN 'OUT_OF_SERVICE'
            ELSE 'ACTIVE'
        END;

        ALTER TABLE machinedetails DROP COLUMN machinestatus;
        ALTER TABLE machinedetails RENAME COLUMN machinestatus_new TO machinestatus;
    END IF;
END $$;

UPDATE machinedetails
SET machinestatus = 'ACTIVE'
WHERE machinestatus IS NULL OR machinestatus = '';

ALTER TABLE machinedetails
    ALTER COLUMN machinestatus TYPE VARCHAR(30);

ALTER TABLE machinedetails
    ALTER COLUMN machinestatus SET NOT NULL;

ALTER TABLE machinedetails
    ALTER COLUMN machinestatus SET DEFAULT 'ACTIVE';

CREATE INDEX IF NOT EXISTS idx_machinedetails_workcenterid
    ON machinedetails(workcenterid);
