-- Replace free-text costCenter with FK to workCenter on purchaserequisition

ALTER TABLE purchaserequisition
    DROP COLUMN IF EXISTS costcenter;

ALTER TABLE purchaserequisition
    ADD COLUMN IF NOT EXISTS workCenterId INTEGER REFERENCES workCenter(id);

CREATE INDEX IF NOT EXISTS idx_pr_workcenter ON purchaserequisition(workCenterId);
