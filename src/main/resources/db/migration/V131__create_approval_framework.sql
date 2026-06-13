-- V131: Configurable approval framework (lives in common, used by all modules).
-- MT note: per-tenant (no schema prefix). Mirror as V131_1__ in MT fork.

CREATE TABLE IF NOT EXISTS approvalpolicy (
    id              BIGSERIAL PRIMARY KEY,
    documentType    VARCHAR(40) NOT NULL,
    description     VARCHAR(200),
    enabled         BOOLEAN NOT NULL DEFAULT false,
    creationDate    TIMESTAMP NOT NULL DEFAULT NOW(),
    updatedDate     TIMESTAMP NOT NULL DEFAULT NOW(),
    deletedDate     TIMESTAMP,
    UNIQUE (documentType)
);

CREATE TABLE IF NOT EXISTS approvalrule (
    id              BIGSERIAL PRIMARY KEY,
    policy_id       BIGINT NOT NULL REFERENCES approvalpolicy(id),
    sortOrder       INTEGER NOT NULL DEFAULT 0,
    -- conditions: JSON array, e.g. [{"attribute":"amount","operator":">=","value":"50000"}]
    --
    -- Boolean semantics (disjunctive normal form — OR of ANDs):
    --   * ALL conditions WITHIN a rule must match (logical AND).
    --   * A document needs approval if ANY rule matches (logical OR across rules
    --     at the same level — they collapse to one step for that level).
    --   * Rules at DIFFERENT levels form an escalation chain — each level is a
    --     separate required approval step.
    -- To express "A AND B": one rule with both conditions.
    -- To express "A OR B":  two rules (same level/role), one condition each.
    conditions      TEXT NOT NULL DEFAULT '[]',
    approverRole    VARCHAR(100) NOT NULL,
    level           INTEGER NOT NULL DEFAULT 1,
    description     VARCHAR(200),
    deletedDate     TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_approvalrule_policy ON approvalrule(policy_id);

CREATE TABLE IF NOT EXISTS approvalrequest (
    id              BIGSERIAL PRIMARY KEY,
    documentType    VARCHAR(40) NOT NULL,
    documentId      BIGINT NOT NULL,
    status          VARCHAR(12) NOT NULL DEFAULT 'PENDING'
        CHECK (status IN ('PENDING','APPROVED','REJECTED','CANCELLED')),
    requestedBy     VARCHAR(100),
    requestedAt     TIMESTAMP NOT NULL DEFAULT NOW(),
    resolvedAt      TIMESTAMP,
    UNIQUE (documentType, documentId)
);

CREATE INDEX IF NOT EXISTS idx_approvalrequest_doc ON approvalrequest(documentType, documentId);

CREATE TABLE IF NOT EXISTS approvalstep (
    id              BIGSERIAL PRIMARY KEY,
    request_id      BIGINT NOT NULL REFERENCES approvalrequest(id),
    level           INTEGER NOT NULL,
    approverRole    VARCHAR(100) NOT NULL,
    status          VARCHAR(10) NOT NULL DEFAULT 'PENDING'
        CHECK (status IN ('PENDING','APPROVED','REJECTED')),
    actedBy         VARCHAR(100),
    actedAt         TIMESTAMP,
    comment         VARCHAR(500)
);

CREATE INDEX IF NOT EXISTS idx_approvalstep_request ON approvalstep(request_id);
CREATE INDEX IF NOT EXISTS idx_approvalstep_role    ON approvalstep(approverRole, status);

-- Seed a default MANUAL_VOUCHER policy (disabled by default — auto-approve unless enabled)
INSERT INTO approvalpolicy (documentType, description, enabled)
VALUES ('MANUAL_VOUCHER', 'Approval policy for manually entered accounting vouchers', false)
ON CONFLICT (documentType) DO NOTHING;
