-- Phase 4 (TDS) — section master, deduction fields on vendor payment, and the deductee ledger.
-- TDS is deducted AT PAYMENT (decision locked 2026-06-20): the VendorPayment carries section/rate/amount,
-- the accounting side mirrors each deduction into tdsentry (the 26Q source) and posts Cr TDS Payable (9015).
-- Vendor is still settled at the full `amount` (debits the bill); net bank outflow = amount - tdsAmount.

CREATE TABLE tdssection (
    id               BIGSERIAL     PRIMARY KEY,
    section          VARCHAR(20)   NOT NULL UNIQUE,
    description      VARCHAR(200)  NOT NULL,
    rate             NUMERIC(6,3)  NOT NULL,
    panMissingRate   NUMERIC(6,3)  NOT NULL DEFAULT 20,
    thresholdSingle  NUMERIC(14,2),
    thresholdAnnual  NUMERIC(14,2),
    applicableTo     VARCHAR(30)   NOT NULL DEFAULT 'VENDOR_PAYMENT'
        CHECK (applicableTo IN ('VENDOR_PAYMENT')),
    active           BOOLEAN       NOT NULL DEFAULT TRUE,
    deletedDate      TIMESTAMP
);

-- Common resident, non-salary sections. Rates are statutory defaults; thresholds are advisory (UI hint).
INSERT INTO tdssection (section, description, rate, panMissingRate, thresholdSingle, thresholdAnnual) VALUES
    ('194C',     'Payment to contractors (company)',        2,   20, 30000,  100000),
    ('194C-IND', 'Payment to contractors (individual/HUF)', 1,   20, 30000,  100000),
    ('194J',     'Professional / technical fees',           10,  20, 30000,  NULL),
    ('194Q',     'Purchase of goods over Rs 50 lakh',       0.1, 5,  NULL,   5000000),
    ('194I-P',   'Rent - plant & machinery',                2,   20, 240000, NULL),
    ('194I-B',   'Rent - land / building',                  10,  20, 240000, NULL),
    ('194H',     'Commission / brokerage',                  5,   20, 15000,  NULL),
    ('194A',     'Interest other than on securities',       10,  20, 5000,   NULL);

-- TDS withheld on a vendor payment. Loose coupling by section code (no FK into the accounting module).
ALTER TABLE vendorPayment ADD COLUMN tdsSectionCode VARCHAR(20);
ALTER TABLE vendorPayment ADD COLUMN tdsRate        NUMERIC(6,3);
ALTER TABLE vendorPayment ADD COLUMN tdsAmount      NUMERIC(12,2) NOT NULL DEFAULT 0;

-- Deductee-wise TDS ledger — the source for the 26Q return. One row per deduction;
-- idempotent on (sourceDocType, sourceDocId). challanId is set when the quarter is deposited (V144).
CREATE TABLE tdsentry (
    id               BIGSERIAL     PRIMARY KEY,
    section_id       BIGINT        NOT NULL REFERENCES tdssection(id),
    contact_id       INTEGER       NOT NULL REFERENCES contact(id),
    sourceDocType    VARCHAR(40)   NOT NULL,
    sourceDocId      BIGINT        NOT NULL,
    taxableAmount    NUMERIC(14,2) NOT NULL,
    tdsAmount        NUMERIC(14,2) NOT NULL,
    rate             NUMERIC(6,3)  NOT NULL,
    financialYear    VARCHAR(9)    NOT NULL,
    quarter          VARCHAR(2)    NOT NULL CHECK (quarter IN ('Q1','Q2','Q3','Q4')),
    deductionDate    DATE          NOT NULL,
    status           VARCHAR(12)   NOT NULL DEFAULT 'DEDUCTED'
        CHECK (status IN ('DEDUCTED','DEPOSITED')),
    challanId        BIGINT,
    deletedDate      TIMESTAMP
);

CREATE UNIQUE INDEX uq_tdsentry_source     ON tdsentry(sourceDocType, sourceDocId) WHERE deletedDate IS NULL;
CREATE INDEX        idx_tdsentry_fy_quarter ON tdsentry(financialYear, quarter);
CREATE INDEX        idx_tdsentry_status     ON tdsentry(status);
CREATE INDEX        idx_tdsentry_contact    ON tdsentry(contact_id);
CREATE INDEX        idx_tdsentry_challan    ON tdsentry(challanId);
