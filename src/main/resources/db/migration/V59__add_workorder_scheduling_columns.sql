-- ============================================================================
-- V59: Add scheduling & priority columns to WorkOrder
-- Phase 1: WorkOrder Foundation
-- ============================================================================

-- Priority (4 levels: URGENT, HIGH, NORMAL, LOW)
ALTER TABLE workorder
    ADD COLUMN IF NOT EXISTS priority VARCHAR(20) NOT NULL DEFAULT 'NORMAL';

-- Estimated production time and cost
ALTER TABLE workorder
    ADD COLUMN IF NOT EXISTS estimatedproductionminutes NUMERIC(15,2),
    ADD COLUMN IF NOT EXISTS estimatedtotalcost NUMERIC(15,2);

-- Scheduling metadata
ALTER TABLE workorder
    ADD COLUMN IF NOT EXISTS autoscheduled BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS scheduledby VARCHAR(100),
    ADD COLUMN IF NOT EXISTS scheduledat TIMESTAMP;

-- Index for priority-based sorting (used by scheduler)
CREATE INDEX IF NOT EXISTS idx_wo_priority
    ON workorder(priority);

-- Index for due-date based scheduling queries
CREATE INDEX IF NOT EXISTS idx_wo_duedate
    ON workorder(duedate);

-- Composite index for scheduler: find unscheduled work orders by priority
CREATE INDEX IF NOT EXISTS idx_wo_status_priority
    ON workorder(workorderstatus, priority);
