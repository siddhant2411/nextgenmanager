-- V129: Financial Year and Accounting Period tables.
-- MT note: per-tenant (no schema prefix). Mirror as V129_1__ in MT fork.

CREATE TABLE IF NOT EXISTS financialyear (
    id                      BIGSERIAL PRIMARY KEY,
    label                   VARCHAR(20) NOT NULL,
    startDate               DATE NOT NULL,
    endDate                 DATE NOT NULL,
    openingDate             DATE,
    status                  VARCHAR(10) NOT NULL DEFAULT 'ACTIVE'
        CHECK (status IN ('ACTIVE','CLOSED')),
    openingEntryVoucher_id  BIGINT,
    creationDate            TIMESTAMP NOT NULL DEFAULT NOW(),
    updatedDate             TIMESTAMP NOT NULL DEFAULT NOW(),
    UNIQUE (startDate, endDate)
);

CREATE TABLE IF NOT EXISTS accountingperiod (
    id                BIGSERIAL PRIMARY KEY,
    financialYear_id  BIGINT NOT NULL REFERENCES financialyear(id),
    periodNumber      INTEGER NOT NULL CHECK (periodNumber BETWEEN 1 AND 12),
    startDate         DATE NOT NULL,
    endDate           DATE NOT NULL,
    status            VARCHAR(10) NOT NULL DEFAULT 'OPEN'
        CHECK (status IN ('OPEN','LOCKED','CLOSED')),
    UNIQUE (financialYear_id, periodNumber)
);

CREATE INDEX IF NOT EXISTS idx_accountingperiod_fy     ON accountingperiod(financialYear_id);
CREATE INDEX IF NOT EXISTS idx_accountingperiod_dates  ON accountingperiod(startDate, endDate);
