-- Phase 4 (TDS) — challan deposit + deductor TAN for the 26Q return.
-- A challan aggregates the deducted-but-not-deposited tdsentry rows of a FY+quarter, flips them
-- to DEPOSITED, and posts Dr TDS Payable (9015) / Cr Bank.

CREATE TABLE tdschallan (
    id             BIGSERIAL     PRIMARY KEY,
    challanNumber  VARCHAR(50)   NOT NULL,
    bsrCode        VARCHAR(20),
    depositDate    DATE          NOT NULL,
    amount         NUMERIC(14,2) NOT NULL,
    section        VARCHAR(20),
    financialYear  VARCHAR(9)    NOT NULL,
    quarter        VARCHAR(2)    NOT NULL CHECK (quarter IN ('Q1','Q2','Q3','Q4')),
    notes          VARCHAR(500),
    createdBy      VARCHAR(100),
    creationDate   TIMESTAMP     NOT NULL DEFAULT NOW(),
    updatedDate    TIMESTAMP     NOT NULL DEFAULT NOW(),
    deletedDate    TIMESTAMP
);

CREATE INDEX idx_tdschallan_fy ON tdschallan(financialYear);

-- Now that tdschallan exists, harden the loose id link on tdsentry into a real FK.
ALTER TABLE tdsentry ADD CONSTRAINT fk_tdsentry_challan FOREIGN KEY (challanId) REFERENCES tdschallan(id);

-- Deductor TAN — required as the 26Q return header (TAN belongs to the deductor, not the contact).
ALTER TABLE company_details ADD COLUMN tan VARCHAR(15);
