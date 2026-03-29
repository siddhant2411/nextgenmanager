CREATE TABLE bomaudit (
    id BIGSERIAL PRIMARY KEY,

    bomId INTEGER NOT NULL,

    oldStatus VARCHAR(50),
    newStatus VARCHAR(50),

    changedAt TIMESTAMPTZ NOT NULL,

    changedBy VARCHAR(100),            -- user or SYSTEM
    source VARCHAR(50),                 -- UI, API, SYSTEM, WORKFLOW
    comment VARCHAR(2000),              -- optional notes or approval reason

    payloadJson TEXT                   -- optional full event payload
);

CREATE INDEX idx_bom_audit_bom_id ON bomaudit (bomId);
CREATE INDEX idx_bom_audit_changed_at ON bomaudit (changedAt DESC);


CREATE TABLE bomHistory (
    id BIGSERIAL PRIMARY KEY,

    bomId INTEGER NOT NULL,

    versionNumber INTEGER,
    revision VARCHAR(50),

    changedAt TIMESTAMPTZ NOT NULL,
    changedBy VARCHAR(100),

    changeType VARCHAR(100),          -- CREATED / UPDATED / STRUCTURE_CHANGE / VERSION_BUMP

    snapshotJson TEXT NOT NULL,        -- full BOM snapshot (JSON)
    changeSummary VARCHAR(2000)        -- readable explanation of what changed
);

CREATE INDEX idx_bom_history_bom_id ON bomHistory (bomId);
CREATE INDEX idx_bom_history_version_number ON bomHistory (bomId, versionNumber);
CREATE INDEX idx_bom_history_changed_at ON bomHistory (changedAt DESC);


CREATE TABLE componentAudit (
    id BIGSERIAL PRIMARY KEY,

    componentId INTEGER NOT NULL,

    fieldChanged VARCHAR(255),     -- name, description, quantity, etc.
    oldValue TEXT,                 -- previous value
    newValue TEXT,                 -- updated value

    changedAt TIMESTAMPTZ NOT NULL,

    changedBy VARCHAR(100),        -- username or 'SYSTEM'
    source VARCHAR(50),             -- UI, API, SYSTEM
    comment VARCHAR(2000),          -- optional comment or reason

    payloadJson TEXT               -- optional serialized JSON payload
);

-- Recommended indexes
CREATE INDEX idx_component_audit_component_id ON componentAudit (componentId);
CREATE INDEX idx_component_audit_changed_at ON componentAudit (changedAt DESC);
