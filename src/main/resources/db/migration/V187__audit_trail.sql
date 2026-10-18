-- V187: audit trail (Companies (Accounts) Rules 2014, Rule 3(1) "edit log").
--   audittrail — one row per change to the books or to the masters behind them: who, when, what,
--                and the before/after values. Rows are only ever inserted; the trigger below rejects
--                UPDATE, DELETE and TRUNCATE so the log cannot be edited or switched off from SQL either.

CREATE TABLE IF NOT EXISTS audittrail (
    id            BIGSERIAL PRIMARY KEY,
    eventTime     TIMESTAMP NOT NULL DEFAULT NOW(),
    username      VARCHAR(100),                    -- null only for work no signed-in user started
    action        VARCHAR(40)  NOT NULL,           -- CREATE, UPDATE, DELETE, POST, SUBMIT, APPROVE, REVERSE, VOID, AUTO_REVERSAL_FAILED
    entityType    VARCHAR(40)  NOT NULL,           -- LEDGER, ACCOUNT_GROUP, VOUCHER, SALES_PAYMENT, VENDOR_PAYMENT, TAX_INVOICE, ...
    entityId      BIGINT,
    entityLabel   VARCHAR(200),                    -- the code / number a person recognises
    summary       VARCHAR(500),
    details       TEXT,                            -- JSON: a snapshot, or {field: {from, to}} for an edit
    voucherId     BIGINT                           -- the voucher the event concerns, when there is one
);
CREATE INDEX IF NOT EXISTS idx_audittrail_time   ON audittrail(eventTime);
CREATE INDEX IF NOT EXISTS idx_audittrail_entity ON audittrail(entityType, entityId);
CREATE INDEX IF NOT EXISTS idx_audittrail_action ON audittrail(action);

CREATE OR REPLACE FUNCTION audittrail_append_only() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'audittrail is append-only: % is not allowed', TG_OP;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_audittrail_no_update ON audittrail;
CREATE TRIGGER trg_audittrail_no_update
    BEFORE UPDATE OR DELETE ON audittrail
    FOR EACH ROW EXECUTE FUNCTION audittrail_append_only();

DROP TRIGGER IF EXISTS trg_audittrail_no_truncate ON audittrail;
CREATE TRIGGER trg_audittrail_no_truncate
    BEFORE TRUNCATE ON audittrail
    FOR EACH STATEMENT EXECUTE FUNCTION audittrail_append_only();
