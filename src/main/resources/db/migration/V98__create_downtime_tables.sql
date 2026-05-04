-- V98: Downtime Reason Tracking & OEE support
-- Adds downtimeReasonCode master and downtimeEvent transaction tables.

-- ─── 1. Downtime Reason Code master ──────────────────────────────────────────
CREATE TABLE IF NOT EXISTS downtimeReasonCode (
    id            BIGSERIAL    PRIMARY KEY,
    code          VARCHAR(50)  UNIQUE NOT NULL,
    description   VARCHAR(255),
    category      VARCHAR(20)  NOT NULL DEFAULT 'UNPLANNED',
    isActive      BOOLEAN      NOT NULL DEFAULT TRUE,
    creationDate  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Seed common MSME downtime codes
INSERT INTO downtimeReasonCode (code, description, category) VALUES
    ('MB01',  'Machine Breakdown',          'UNPLANNED'),
    ('POW01', 'Power Failure',              'UNPLANNED'),
    ('NO_OP', 'No Operator Available',      'UNPLANNED'),
    ('TOOL1', 'Tooling Issue',              'UNPLANNED'),
    ('MAT1',  'Material Shortage',         'UNPLANNED'),
    ('SCHED', 'Scheduled Maintenance',     'PLANNED'),
    ('CLEAN', 'Cleaning / 5S Activity',    'PLANNED'),
    ('BREAK', 'Meal / Tea Break',          'PLANNED')
ON CONFLICT (code) DO NOTHING;

-- ─── 2. Downtime Event transaction table ──────────────────────────────────────
CREATE TABLE IF NOT EXISTS downtimeEvent (
    id                      BIGSERIAL   PRIMARY KEY,
    machineId               BIGINT      NOT NULL REFERENCES MachineDetails(id),
    reasonCodeId            BIGINT      NOT NULL REFERENCES downtimeReasonCode(id),
    shiftId                 BIGINT,
    workOrderOperationId    BIGINT      REFERENCES WorkOrderOperation(id),
    startTime               TIMESTAMP   NOT NULL,
    endTime                 TIMESTAMP,
    durationMinutes         INTEGER,
    remarks                 VARCHAR(500),
    reportedBy              VARCHAR(100),
    createdAt               TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_dt_machine ON downtimeEvent (machineId);
CREATE INDEX IF NOT EXISTS idx_dt_active  ON downtimeEvent (machineId, endTime);
