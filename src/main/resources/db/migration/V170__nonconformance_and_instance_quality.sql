-- Phase H step 2 — what happens to goods that failed.
--
-- Step 1 gave quality a verdict that stops a work order. This gives the failure somewhere to go.
--
-- Two holes are closed here. A rejected quantity on a goods receipt has always been typed in and
-- then dropped: only the accepted quantity produced stock, so goods physically standing in the
-- building were on nobody's books, and the quarantine warehouse phase F created had never
-- received anything. And an instance's quality was unknowable — QualityStatus existed only on
-- batch and serial records, so untracked stock had no way to say it had failed.

CREATE TABLE nonconformancereport (
    id                 BIGSERIAL     PRIMARY KEY,
    ncrNumber          VARCHAR(30)   NOT NULL,
    inspectionlot_id   BIGINT        NOT NULL REFERENCES inspectionlot (id),

    quantity           NUMERIC(18,4) NOT NULL,
    problem            VARCHAR(1000) NOT NULL,

    disposition        VARCHAR(20)
        CHECK (disposition IN ('REWORK','SCRAP','USE_AS_IS','RETURN_TO_VENDOR')),
    dispositionedBy    VARCHAR(100),
    dispositionedDate  TIMESTAMP,
    dispositionNotes   VARCHAR(1000),

    -- Releasing goods that failed is a decision somebody owns, exactly like waiving a lot. Kept
    -- beside the disposition rather than inferred from it, so a use-as-is a year old still names
    -- the person who agreed to it.
    approvedBy         VARCHAR(100),

    status             VARCHAR(20)   NOT NULL DEFAULT 'OPEN'
        CHECK (status IN ('OPEN','CLOSED')),
    raisedBy           VARCHAR(100),
    remarks            VARCHAR(500),
    creationDate       TIMESTAMP,
    updatedDate        TIMESTAMP,
    deletedDate        TIMESTAMP,

    CONSTRAINT ck_ncr_quantity_positive CHECK (quantity > 0),
    -- A report cannot be closed without saying what was done about it.
    CONSTRAINT ck_ncr_closed_has_disposition CHECK (status <> 'CLOSED' OR disposition IS NOT NULL),
    -- Nor can goods be used as they are without somebody's name against it.
    CONSTRAINT ck_ncr_use_as_is_approved CHECK (disposition <> 'USE_AS_IS' OR approvedBy IS NOT NULL)
);

COMMENT ON TABLE nonconformancereport IS
    'What was decided about goods that failed inspection: reworked, scrapped, used as they are, or
     sent back to the vendor.';

CREATE UNIQUE INDEX ux_ncr_number ON nonconformancereport (ncrNumber) WHERE deletedDate IS NULL;
CREATE INDEX idx_ncr_lot    ON nonconformancereport (inspectionlot_id);
CREATE INDEX idx_ncr_status ON nonconformancereport (status) WHERE deletedDate IS NULL;

-- Whether this particular stock is fit to use.
--
-- Existing rows become PASSED rather than PENDING_QC: every unit already in stock was accepted
-- into it without an inspection lot, and marking the lot of them as awaiting QC would strand real
-- stock behind a gate nobody can clear. The honest default for stock already on the shelf is that
-- it was accepted.
ALTER TABLE inventoryinstance
    ADD COLUMN qualityStatus VARCHAR(20) NOT NULL DEFAULT 'PASSED'
        CHECK (qualityStatus IN ('PENDING_QC','PASSED','FAILED','WAIVED'));

COMMENT ON COLUMN inventoryinstance.qualityStatus IS
    'PASSED and WAIVED stock can be picked; PENDING_QC and FAILED cannot. Until now this lived
     only on batch and serial records, so untracked stock had no way to say it had failed.';

CREATE INDEX idx_inventoryinstance_quality
    ON inventoryinstance (qualityStatus)
    WHERE qualityStatus <> 'PASSED';
