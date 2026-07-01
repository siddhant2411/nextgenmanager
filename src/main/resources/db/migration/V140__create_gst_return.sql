-- ── GST (Phase 2): filed-return snapshot ────────────────────────────────────
-- Captures each GSTR-1 / GSTR-3B filing: period, totals and the full JSON payload.
-- Registers and the live returns are projections over source docs (no parallel tax
-- store); only the FILED snapshot is persisted. One live return per (period, type).
CREATE TABLE gstreturn (
    id               BIGSERIAL PRIMARY KEY,
    financialYear_id BIGINT       NOT NULL REFERENCES financialyear(id),
    period_id        BIGINT       NOT NULL REFERENCES accountingperiod(id),
    returnType       VARCHAR(10)  NOT NULL CHECK (returnType IN ('GSTR1', 'GSTR3B')),
    status           VARCHAR(10)  NOT NULL DEFAULT 'FILED' CHECK (status IN ('DRAFT', 'FILED')),
    periodLabel      VARCHAR(40),
    taxableValue     NUMERIC(14,2) NOT NULL DEFAULT 0,
    cgst             NUMERIC(14,2) NOT NULL DEFAULT 0,
    sgst             NUMERIC(14,2) NOT NULL DEFAULT 0,
    igst             NUMERIC(14,2) NOT NULL DEFAULT 0,
    cess             NUMERIC(14,2) NOT NULL DEFAULT 0,
    total            NUMERIC(14,2) NOT NULL DEFAULT 0,
    payload          TEXT,
    filedBy          VARCHAR(100),
    filedDate        TIMESTAMP,
    creationDate     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updatedDate      TIMESTAMP,
    deletedDate      TIMESTAMP
);

-- One live (non-deleted) return per period per type; superseded filings are soft-deleted.
CREATE UNIQUE INDEX uq_gstreturn_period_type
    ON gstreturn (period_id, returnType) WHERE deletedDate IS NULL;
CREATE INDEX idx_gstreturn_fy ON gstreturn (financialYear_id);
