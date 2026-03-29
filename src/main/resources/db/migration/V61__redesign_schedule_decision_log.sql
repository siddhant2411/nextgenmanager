-- ============================================================================
-- V61: Redesign ScheduleDecisionLog for production scheduling
-- Phase 2: Production Scheduling Engine
--
-- Old table: orderId + machineId + scheduledStart/End
-- New table: workOrderId + workOrderOperationId + workCenterId
--            + scheduledDate + availableMinutes + consumedMinutes
-- ============================================================================

-- Drop old indexes and constraints
DROP INDEX IF EXISTS idx_sdl_machine_time;
ALTER TABLE scheduledecisionlog DROP CONSTRAINT IF EXISTS fk_sdl_machine;

-- Drop old columns
ALTER TABLE scheduledecisionlog
    DROP COLUMN IF EXISTS orderid,
    DROP COLUMN IF EXISTS machineid,
    DROP COLUMN IF EXISTS scheduledstart,
    DROP COLUMN IF EXISTS scheduledend;

-- Add new columns
ALTER TABLE scheduledecisionlog
    ADD COLUMN IF NOT EXISTS workorderid BIGINT,
    ADD COLUMN IF NOT EXISTS workorderoperationid BIGINT,
    ADD COLUMN IF NOT EXISTS workcenterid BIGINT,
    ADD COLUMN IF NOT EXISTS scheduleddate TIMESTAMP,
    ADD COLUMN IF NOT EXISTS availableminutes INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS consumedminutes INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS createdat TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP;

-- Add foreign keys
ALTER TABLE scheduledecisionlog
    ADD CONSTRAINT fk_sdl_work_order
        FOREIGN KEY (workorderid) REFERENCES workorder(id),
    ADD CONSTRAINT fk_sdl_work_order_operation
        FOREIGN KEY (workorderoperationid) REFERENCES workorderoperation(id),
    ADD CONSTRAINT fk_sdl_work_center
        FOREIGN KEY (workcenterid) REFERENCES workcenter(id);

-- Create new indexes
CREATE INDEX IF NOT EXISTS idx_sdl_wo
    ON scheduledecisionlog(workorderid);

CREATE INDEX IF NOT EXISTS idx_sdl_wc_date
    ON scheduledecisionlog(workcenterid, scheduleddate);
