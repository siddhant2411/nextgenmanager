-- Generic document checklist used by Purchase, Accounts, and any future module.

CREATE TABLE IF NOT EXISTS documentChecklistItem (
    id                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    entityType          VARCHAR(50)     NOT NULL,
    entityId            BIGINT          NOT NULL,
    documentType        VARCHAR(30)     NOT NULL,
    status              VARCHAR(20)     NOT NULL DEFAULT 'PENDING',
    fileAttachmentId    BIGINT          REFERENCES fileAttachment(id),
    fileName            VARCHAR(255),
    fileUrl             VARCHAR(2048),
    receivedDate        TIMESTAMP,
    notes               VARCHAR(500),
    createdDate         TIMESTAMP,
    updatedDate         TIMESTAMP,
    CONSTRAINT uq_checklist_entity_doctype UNIQUE (entityType, entityId, documentType)
);

CREATE INDEX IF NOT EXISTS idx_checklist_entity
    ON documentChecklistItem(entityType, entityId);
