-- Item revision control — see docs/ITEM_REVISION_CONTROL_PLAN.md
--
-- InventoryItem.revision was a smallint nobody ever read (typed into a UI box, never checked by
-- any backend code). BOMs already have real versioning; items had none, which is backwards — the
-- part is the thing a supplier actually makes. This gives the item its own revision history and,
-- separately, its own lock: a RELEASED revision freezes the engineering fields (dimension, size,
-- weight, material, process, drawing number, UoM, HSN) while price and stock policy stay editable
-- on the item itself, always.

CREATE TABLE itemRevision (
    id                 BIGSERIAL     PRIMARY KEY,
    inventoryItemId    INTEGER       NOT NULL REFERENCES inventoryItem (inventoryItemId),

    revisionCode       VARCHAR(10)   NOT NULL,
    status             VARCHAR(20)   NOT NULL
        CHECK (status IN ('DRAFT','PENDING_APPROVAL','RELEASED','SUPERSEDED','OBSOLETE')),

    -- Null until answered at release: does this revision keep prior stock/POs valid, or does it
    -- need a where-used impact review? See the plan doc §5.
    interchangeable    BOOLEAN,

    ecoNumber          VARCHAR(60),
    changeReason       VARCHAR(1000),
    releasedBy         VARCHAR(100),
    releasedOn         TIMESTAMP,
    supersededById     BIGINT        REFERENCES itemRevision (id),
    effectiveFrom      TIMESTAMP,
    effectiveTo        TIMESTAMP,

    -- Engineering attributes — what productSpecification held, now versioned.
    dimension          VARCHAR(255),
    size               VARCHAR(255),
    weight             VARCHAR(255),
    basicMaterial      VARCHAR(255),
    processType        VARCHAR(255),
    drawingNumber      VARCHAR(255),
    uom                SMALLINT CHECK (uom IS NULL OR (uom >= 0 AND uom <= 11)),
    hsnCode            VARCHAR(20),

    creationDate       TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updatedDate        TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deletedDate        TIMESTAMP,

    CONSTRAINT uq_item_revision_code UNIQUE (inventoryItemId, revisionCode)
);

CREATE INDEX idx_item_revision_item ON itemRevision (inventoryItemId);

-- At most one DRAFT open per item at a time — "Revise" is refused while one already exists.
CREATE UNIQUE INDEX uq_item_revision_one_draft ON itemRevision (inventoryItemId)
    WHERE status = 'DRAFT';

ALTER TABLE inventoryItem
    ADD COLUMN currentRevisionId BIGINT REFERENCES itemRevision (id);

-- Nullable during migration: existing BOM positions are backfilled in the next migration, new
-- ones are resolved at add/edit time from the child item's current released revision.
ALTER TABLE bomPosition
    ADD COLUMN childItemRevisionId BIGINT REFERENCES itemRevision (id);
