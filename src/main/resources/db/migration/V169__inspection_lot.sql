-- Phase H step 1 — QC as a gate, not a log.
--
-- Quality has been recorded for a long time and acted on nowhere: a work order whose critical QA
-- parameter failed still completes, and the finished goods still land on the shelf. What is
-- missing is not more measurement — BomQaParameter, WorkOrderQaEntry and WorkOrderQaResult
-- already cover in-process inspection in detail — but a verdict something downstream refuses to
-- ignore.
--
-- The inspection lot is that verdict: this quantity of this item, arising from this document, was
-- inspected, and here is the answer. Deliberately NOT included here:
--   * results for in-process lots. The operator's measurements already live on the operation, and
--     a second place to type them in is how two records of one inspection start disagreeing. An
--     in-process lot rolls those up rather than copying them.
--   * non-conformance reports and their dispositions, quality status on inventory instances, and
--     routing rejected goods into the quarantine warehouse — all step 2.

CREATE TABLE inspectionlot (
    id                  BIGSERIAL     PRIMARY KEY,
    lotNumber           VARCHAR(30)   NOT NULL,
    source              VARCHAR(20)   NOT NULL
        CHECK (source IN ('INCOMING','IN_PROCESS','FINAL','PACKAGE')),
    status              VARCHAR(20)   NOT NULL DEFAULT 'PENDING'
        CHECK (status IN ('PENDING','PASSED','FAILED','WAIVED')),

    inventoryItemRef    INTEGER       NOT NULL REFERENCES inventoryItem (inventoryItemId),

    -- Exactly one of these is set, according to source. Kept as separate typed columns rather
    -- than a polymorphic id + type pair, so the database can still enforce that the document
    -- being inspected actually exists.
    goodsReceiptNote_id BIGINT        REFERENCES goodsreceiptnote (id),
    workOrder_id        INTEGER       REFERENCES workorder (id),
    workOrderOperation_id BIGINT      REFERENCES workorderoperation (id),

    quantityOffered     NUMERIC(18,4) NOT NULL,
    quantityAccepted    NUMERIC(18,4) NOT NULL DEFAULT 0,
    quantityRejected    NUMERIC(18,4) NOT NULL DEFAULT 0,

    inspectedBy         VARCHAR(100),
    inspectedDate       TIMESTAMP,
    waivedBy            VARCHAR(100),
    waiverReason        VARCHAR(500),
    remarks             VARCHAR(500),
    createdBy           VARCHAR(100),
    creationDate        TIMESTAMP,
    updatedDate         TIMESTAMP,
    deletedDate         TIMESTAMP,

    CONSTRAINT ck_inspectionlot_offered_positive CHECK (quantityOffered > 0),
    CONSTRAINT ck_inspectionlot_split CHECK (
        quantityAccepted >= 0
        AND quantityRejected >= 0
        AND quantityAccepted + quantityRejected <= quantityOffered),
    -- A lot has to be about a document. Which one depends on the source, and the source is the
    -- only thing that decides it.
    CONSTRAINT ck_inspectionlot_source_document CHECK (
        (source = 'INCOMING'   AND goodsReceiptNote_id IS NOT NULL)
     OR (source = 'IN_PROCESS' AND workOrderOperation_id IS NOT NULL)
     OR (source = 'FINAL'      AND workOrder_id IS NOT NULL)
     OR (source = 'PACKAGE'))
);

COMMENT ON TABLE inspectionlot IS
    'One inspection and its verdict. The gate reads status: PASSED and WAIVED let a movement
     through, PENDING and FAILED stop it.';
COMMENT ON COLUMN inspectionlot.quantityRejected IS
    'Offered minus accepted is not assumed to be rejected: a part-inspected lot has units that are
     neither yet. Step 2 routes rejected quantity into the quarantine warehouse.';
COMMENT ON COLUMN inspectionlot.waivedBy IS
    'A waiver is a decision someone owns. Recorded here rather than inferred from a status, so a
     lot that went out on a waiver can still be found a year later.';

CREATE UNIQUE INDEX ux_inspectionlot_number ON inspectionlot (lotNumber) WHERE deletedDate IS NULL;
CREATE INDEX idx_inspectionlot_item      ON inspectionlot (inventoryItemRef);
CREATE INDEX idx_inspectionlot_workorder ON inspectionlot (workOrder_id) WHERE workOrder_id IS NOT NULL;
CREATE INDEX idx_inspectionlot_grn       ON inspectionlot (goodsReceiptNote_id) WHERE goodsReceiptNote_id IS NOT NULL;
CREATE INDEX idx_inspectionlot_status    ON inspectionlot (status) WHERE deletedDate IS NULL;

-- What was actually checked, for the sources that have nowhere else to keep it. An in-process lot
-- has no rows here: its measurements are the WorkOrderQaResult rows on the operation.
CREATE TABLE inspectionresult (
    id                BIGSERIAL      PRIMARY KEY,
    inspectionlot_id  BIGINT         NOT NULL REFERENCES inspectionlot (id),
    parameterName     VARCHAR(200)   NOT NULL,
    parameterType     VARCHAR(30),
    minValue          NUMERIC(18,4),
    maxValue          NUMERIC(18,4),
    unit              VARCHAR(30),
    critical          BOOLEAN        NOT NULL DEFAULT FALSE,
    observedValue     NUMERIC(18,4),
    observedText      VARCHAR(300),
    result            VARCHAR(10)    NOT NULL DEFAULT 'PENDING'
        CHECK (result IN ('PENDING','PASS','FAIL')),
    remarks           VARCHAR(300),
    creationDate      TIMESTAMP
);

COMMENT ON COLUMN inspectionresult.critical IS
    'A failure here fails the whole lot. Non-critical failures are recorded and reported but do
     not on their own stop the goods.';
COMMENT ON COLUMN inspectionresult.observedText IS
    'For visual and other judgement checks, where there is no number to record.';

CREATE INDEX idx_inspectionresult_lot ON inspectionresult (inspectionlot_id);

-- Whether finished goods of this item may be produced without a passed final inspection.
--
-- Defaults FALSE on purpose. Every item in this database has been produced without an inspection
-- lot for its whole life; switching the requirement on everywhere at once would stop a shop floor
-- that has never raised one. The other two halves of the gate — a FAILED lot, and a failed
-- critical parameter — need no configuration and apply immediately.
ALTER TABLE productInventorySettings
    ADD COLUMN finalInspectionRequired BOOLEAN NOT NULL DEFAULT FALSE;

COMMENT ON COLUMN productInventorySettings.finalInspectionRequired IS
    'When true, completing a work order for this item requires a passed or waived FINAL
     inspection lot. When false, only an outright failure blocks completion.';
