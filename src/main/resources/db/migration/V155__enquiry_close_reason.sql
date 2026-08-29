-- Close-reason master for enquiries, plus the FK from enquiry onto it.
--
-- Enquiry.closeReason has always been free text, written straight through by closeEnquiry().
-- On a real register that degenerates fast: PEC's 2026 sheet carries 262 closed enquiries
-- spelled 87 different ways -- "No response", "no response", "No reply", "Not to response",
-- "No respn" are one reason typed five ways, and nothing can be counted. The 14 codes seeded
-- below cover all 262 of those historic entries with none left over.
--
-- closeReason (the free-text column) is deliberately kept. The code is what you report on;
-- the original sentence is what tells you *why* -- "we can't offer 3 mm thickness as it's not
-- possible to bend" is worth more than OUT_OF_SCOPE alone, and throwing it away to enforce a
-- taxonomy would lose the only real content in the column.
--
-- Reasons deactivate rather than delete, so tidying the dropdown never restates closed periods.

CREATE TABLE enquiryCloseReason (
    id           BIGSERIAL    PRIMARY KEY,
    code         VARCHAR(50)  NOT NULL UNIQUE,
    description  VARCHAR(255),
    outcome      VARCHAR(20)  NOT NULL DEFAULT 'LOST'
        CHECK (outcome IN ('WON', 'LOST', 'NO_ENGAGEMENT', 'DECLINED_BY_US', 'DEFERRED', 'INVALID')),
    displayOrder INTEGER      NOT NULL DEFAULT 100,
    isActive     BOOLEAN      NOT NULL DEFAULT TRUE,
    creationDate TIMESTAMP    NOT NULL DEFAULT NOW()
);

ALTER TABLE enquiry
    ADD COLUMN IF NOT EXISTS close_reason_id BIGINT REFERENCES enquiryCloseReason (id);

CREATE INDEX idx_enquiry_close_reason ON enquiry (close_reason_id);

-- Outcome grouping is what makes a win rate computable: LOST is a competitive defeat and belongs
-- in the denominator, DECLINED_BY_US and NO_ENGAGEMENT do not.
INSERT INTO enquiryCloseReason (code, description, outcome, displayOrder) VALUES
    ('WON_PO_RECEIVED',  'Won - purchase order received',              'WON',            10),
    ('LOST_PRICE',       'Lost on price / rate too high',              'LOST',           20),
    ('LOST_COMPETITOR',  'Lost to a competitor or other make',         'LOST',           30),
    ('NO_RESPONSE',      'No response from customer',                  'NO_ENGAGEMENT',  40),
    ('DETAILS_AWAITED',  'Details or drawings never received',         'NO_ENGAGEMENT',  50),
    ('NOT_QUOTED',       'Chose not to quote',                         'DECLINED_BY_US', 60),
    ('OUT_OF_SCOPE',     'Outside our product, size or spec range',    'DECLINED_BY_US', 70),
    ('QTY_TOO_LOW',      'Quantity below our minimum',                 'DECLINED_BY_US', 80),
    ('VENDOR_NO_PRICE',  'Sub-vendor price not received in time',      'DECLINED_BY_US', 90),
    ('CERT_NOT_MET',     'Certification or approval requirement unmet','DECLINED_BY_US', 100),
    ('TENDER_MISSED',    'Tender deadline missed or not participated', 'DECLINED_BY_US', 110),
    ('REGRET',           'Regretted (reason not recorded)',            'DECLINED_BY_US', 120),
    ('PROJECT_DEFERRED', 'Customer project deferred or cancelled',     'DEFERRED',       130),
    ('JUNK_INVALID',     'Junk / not a genuine enquiry',               'INVALID',        140);
