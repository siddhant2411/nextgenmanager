-- Which of an item's attachments is its drawing.
--
-- The item list and BOM screens used to guess on every read: the first attachment whose file name
-- contains the drawing number, or failing that whatever attachment came back first — a photo or a
-- vendor quote as often as a drawing. The drawing is now marked on the attachment itself. The flag
-- lives on the attachment rather than the item so an ordinary item save, which rewrites the item
-- row, cannot clear it, and deleting the file takes the mark with it.
--
-- Only meaningful for referenceType = 'inventoryItem'. At most one per item, kept by the service.
-- IF NOT EXISTS: a local run with ddl-auto on may already have added the column from the entity.
ALTER TABLE fileAttachment
    ADD COLUMN IF NOT EXISTS primaryDrawing BOOLEAN NOT NULL DEFAULT FALSE;

-- Mark what each item links to today, so no existing link changes. Same rule the guess used: the
-- first file whose name contains the drawing number, else the first file; lowest id stands in for
-- "first", which is what the unordered read returned in practice.
WITH live AS (
    SELECT fa.id, fa.referenceId, fa.originalName, ps.drawingNumber
    FROM fileAttachment fa
    LEFT JOIN productSpecification ps ON ps.inventory_item_id = fa.referenceId
    WHERE fa.referenceType = 'inventoryItem'
      AND fa.deletedDate IS NULL
),
ranked AS (
    SELECT id,
           ROW_NUMBER() OVER (
               PARTITION BY referenceId
               ORDER BY CASE
                            WHEN drawingNumber IS NOT NULL AND TRIM(drawingNumber) <> ''
                                 AND POSITION(LOWER(drawingNumber) IN LOWER(originalName)) > 0 THEN 0
                            ELSE 1
                        END,
                        id
           ) AS rn
    FROM live
)
UPDATE fileAttachment fa
SET primaryDrawing = TRUE
FROM ranked r
WHERE fa.id = r.id
  AND r.rn = 1;
