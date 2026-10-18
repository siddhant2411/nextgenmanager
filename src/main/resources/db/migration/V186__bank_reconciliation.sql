-- V186: bank reconciliation.
--   bankstatementmapping — how one bank account's statement export is laid out (columns named by
--                          their header text), saved once and reused on every upload.
--   bankstatementline    — one imported statement row, optionally matched 1:1 to the voucher line
--                          that books it.

CREATE TABLE IF NOT EXISTS bankstatementmapping (
    id                BIGSERIAL PRIMARY KEY,
    ledgerAccount_id  BIGINT NOT NULL REFERENCES ledgeraccount(id),
    headerRow         INTEGER NOT NULL DEFAULT 1,          -- 1-based row holding the column titles
    dateColumn        VARCHAR(120) NOT NULL,
    dateFormat        VARCHAR(40),                          -- e.g. dd/MM/yyyy; blank = try the common ones
    narrationColumn   VARCHAR(120),
    referenceColumn   VARCHAR(120),
    withdrawalColumn  VARCHAR(120),                         -- money out (Dr on the statement)
    depositColumn     VARCHAR(120),                         -- money in (Cr on the statement)
    amountColumn      VARCHAR(120),                         -- single signed column, used when the two above are blank
    balanceColumn     VARCHAR(120),
    -- Book entries dated before this count as already cleared (e.g. the opening bank balance at a
    -- mid-year go-live has no statement row behind it). Null = every book entry needs a statement row.
    reconcileFrom     DATE,
    creationDate      TIMESTAMP NOT NULL DEFAULT NOW(),
    updatedDate       TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_bsm_amounts CHECK (amountColumn IS NOT NULL OR withdrawalColumn IS NOT NULL OR depositColumn IS NOT NULL)
);
CREATE UNIQUE INDEX IF NOT EXISTS ux_bsm_ledger ON bankstatementmapping(ledgerAccount_id);

CREATE TABLE IF NOT EXISTS bankstatementline (
    id                  BIGSERIAL PRIMARY KEY,
    ledgerAccount_id    BIGINT NOT NULL REFERENCES ledgeraccount(id),
    txnDate             DATE NOT NULL,
    narration           VARCHAR(500),
    reference           VARCHAR(120),
    withdrawal          NUMERIC(14,2) NOT NULL DEFAULT 0,
    deposit             NUMERIC(14,2) NOT NULL DEFAULT 0,
    balance             NUMERIC(16,2),
    importBatch         VARCHAR(40) NOT NULL,
    sourceFileName      VARCHAR(255),
    voucherLine_id      BIGINT REFERENCES voucherline(id),
    matchedBy           VARCHAR(100),
    matchedAt           TIMESTAMP,
    creationDate        TIMESTAMP NOT NULL DEFAULT NOW(),
    deletedDate         TIMESTAMP,
    CONSTRAINT chk_bsl_one_side CHECK ((withdrawal > 0 AND deposit = 0) OR (deposit > 0 AND withdrawal = 0))
);
CREATE INDEX IF NOT EXISTS idx_bsl_ledger_date ON bankstatementline(ledgerAccount_id, txnDate);
-- A book entry clears against at most one live statement line.
CREATE UNIQUE INDEX IF NOT EXISTS ux_bsl_voucherline ON bankstatementline(voucherLine_id)
    WHERE voucherLine_id IS NOT NULL AND deletedDate IS NULL;
