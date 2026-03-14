-- ============================================================================
-- V62: Scheduling enhancements — machine assignment, input qty tracking,
--      material consumption, decision log machine tracking
-- ============================================================================

-- WorkOrderOperation: machine assignment + input qty tracking
ALTER TABLE workorderoperation
    ADD COLUMN IF NOT EXISTS assignedmachineid BIGINT,
    ADD COLUMN IF NOT EXISTS availableinputquantity NUMERIC(15,5) NOT NULL DEFAULT 0;

ALTER TABLE workorderoperation
    ADD CONSTRAINT fk_woo_machine
        FOREIGN KEY (assignedmachineid) REFERENCES machinedetails(id);

-- WorkOrderMaterial: consumed qty tracking
ALTER TABLE workordermaterial
    ADD COLUMN IF NOT EXISTS consumedquantity NUMERIC(15,5) NOT NULL DEFAULT 0;

-- ScheduleDecisionLog: machine tracking
ALTER TABLE scheduledecisionlog
    ADD COLUMN IF NOT EXISTS machineid BIGINT;

ALTER TABLE scheduledecisionlog
    ADD CONSTRAINT fk_sdl_machine_v2
        FOREIGN KEY (machineid) REFERENCES machinedetails(id);

-- Index for machine schedule queries (operator task queue)
CREATE INDEX IF NOT EXISTS idx_woo_machine_status
    ON workorderoperation(assignedmachineid, status);

-- Index for machine + date range queries
CREATE INDEX IF NOT EXISTS idx_woo_machine_dates
    ON workorderoperation(assignedmachineid, plannedstartdate);
