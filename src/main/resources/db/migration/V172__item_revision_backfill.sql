-- Backfill: every item that exists before revision control shipped gets one revision, "A",
-- already RELEASED — not DRAFT. Landing the whole item master in draft would instantly block
-- every BOM from leaving DRAFT/PENDING_APPROVAL (see the release gate in BomServiceImpl), since a
-- BOM cannot be approved while any component sits at a non-released revision.
--
-- Runs for every item row, soft-deleted included, so an old BOM position that references a
-- deleted item still finds a revision to point at.

INSERT INTO itemRevision (
    inventoryItemId, revisionCode, status, releasedBy, releasedOn, effectiveFrom,
    dimension, size, weight, basicMaterial, processType, drawingNumber, uom, hsnCode,
    creationDate, updatedDate
)
SELECT
    i.inventoryItemId, 'A', 'RELEASED', 'MIGRATION',
    COALESCE(i.creationDate, CURRENT_TIMESTAMP), COALESCE(i.creationDate, CURRENT_TIMESTAMP),
    ps.dimension, ps.size, ps.weight, ps.basicMaterial, ps.processType, ps.drawingNumber,
    i.uom, i.hsnCode,
    COALESCE(i.creationDate, CURRENT_TIMESTAMP), CURRENT_TIMESTAMP
FROM inventoryItem i
LEFT JOIN productspecification ps ON ps.inventory_item_id = i.inventoryItemId;

UPDATE inventoryItem i
SET currentRevisionId = r.id
FROM itemRevision r
WHERE r.inventoryItemId = i.inventoryItemId
  AND r.revisionCode = 'A';

UPDATE bomPosition bp
SET childItemRevisionId = r.id
FROM itemRevision r
WHERE r.inventoryItemId = bp.childInventoryItemId
  AND r.revisionCode = 'A'
  AND bp.childInventoryItemId IS NOT NULL;
