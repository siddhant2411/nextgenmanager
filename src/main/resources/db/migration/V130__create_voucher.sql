-- V130: Voucher and VoucherLine tables — the double-entry core.
-- MT note: per-tenant (no schema prefix). Mirror as V130_1__ in MT fork.

CREATE TABLE IF NOT EXISTS voucher (
    id                      BIGSERIAL PRIMARY KEY,
    voucherType             VARCHAR(15) NOT NULL
        CHECK (voucherType IN ('JOURNAL','SALES','PURCHASE','RECEIPT','PAYMENT','CONTRA',
                               'CREDIT_NOTE','DEBIT_NOTE','OPENING','CLOSING',
                               'DEPRECIATION','PAYROLL')),
    voucherNumber           VARCHAR(40) UNIQUE NOT NULL,
    date                    DATE NOT NULL,
    narration               VARCHAR(500),
    status                  VARCHAR(18) NOT NULL DEFAULT 'DRAFT'
        CHECK (status IN ('DRAFT','PENDING_APPROVAL','POSTED','REVERSED')),
    sourceDocType           VARCHAR(40),
    sourceDocId             BIGINT,
    financialYear_id        BIGINT NOT NULL REFERENCES financialyear(id),
    period_id               BIGINT NOT NULL REFERENCES accountingperiod(id),
    reversalOf_id           BIGINT REFERENCES voucher(id),
    reversedByVoucher_id    BIGINT REFERENCES voucher(id),
    createdBy               VARCHAR(100),
    creationDate            TIMESTAMP NOT NULL DEFAULT NOW(),
    updatedDate             TIMESTAMP NOT NULL DEFAULT NOW(),
    deletedDate             TIMESTAMP
);

-- Idempotency: one voucher per source document (enforced at service level too).
CREATE UNIQUE INDEX IF NOT EXISTS idx_voucher_sourcedoc
    ON voucher(sourceDocType, sourceDocId)
    WHERE sourceDocType IS NOT NULL AND sourceDocId IS NOT NULL AND deletedDate IS NULL;

CREATE INDEX IF NOT EXISTS idx_voucher_date           ON voucher(date);
CREATE INDEX IF NOT EXISTS idx_voucher_type_status    ON voucher(voucherType, status);
CREATE INDEX IF NOT EXISTS idx_voucher_fy             ON voucher(financialYear_id);
CREATE INDEX IF NOT EXISTS idx_voucher_period         ON voucher(period_id);

CREATE TABLE IF NOT EXISTS voucherline (
    id                  BIGSERIAL PRIMARY KEY,
    voucher_id          BIGINT NOT NULL REFERENCES voucher(id),
    lineNumber          INTEGER NOT NULL DEFAULT 0,
    ledgerAccount_id    BIGINT NOT NULL REFERENCES ledgeraccount(id),
    drAmount            NUMERIC(12,2) NOT NULL DEFAULT 0,
    crAmount            NUMERIC(12,2) NOT NULL DEFAULT 0,
    narration           VARCHAR(300),
    costCenter_id       BIGINT,
    taxType             VARCHAR(6) CHECK (taxType IN ('CGST','SGST','IGST','CESS')),
    hsnSac              VARCHAR(10),
    taxableValue        NUMERIC(12,2),
    taxRate             NUMERIC(5,2),
    deletedDate         TIMESTAMP,
    CONSTRAINT chk_voucherline_amounts CHECK (
        (drAmount > 0 AND crAmount = 0) OR (crAmount > 0 AND drAmount = 0)
    )
);

CREATE INDEX IF NOT EXISTS idx_voucherline_voucher   ON voucherline(voucher_id);
CREATE INDEX IF NOT EXISTS idx_voucherline_account   ON voucherline(ledgerAccount_id);
