-- Add customLabel column for user-defined checklist documents
ALTER TABLE documentchecklistitem
    ADD COLUMN IF NOT EXISTS customLabel VARCHAR(255);

-- Drop the unique constraint that prevented multiple CUSTOM entries per entity
ALTER TABLE documentchecklistitem
    DROP CONSTRAINT IF EXISTS uq_checklist_entity_doctype;

-- Partial unique constraint: prevents duplicate fixed doc types per entity,
-- but allows multiple CUSTOM rows (each with a different label)
CREATE UNIQUE INDEX IF NOT EXISTS uq_checklist_fixed_doctype
    ON documentchecklistitem (entityType, entityId, documentType)
    WHERE documentType <> 'CUSTOM';
