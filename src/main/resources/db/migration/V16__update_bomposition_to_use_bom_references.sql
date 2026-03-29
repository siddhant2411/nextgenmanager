-- ==========================================================
-- Flyway Migration: Update BomPosition to reference BOM instead of InventoryItem
-- ==========================================================


-- 1️⃣ Delete invalid orphan BomPosition rows (no parent BOM)
DELETE FROM bomPosition
WHERE bomPositionId IS NULL;


-- 2️⃣ Add parentBomId column (NULLABLE FIRST!)
ALTER TABLE bomPosition
ADD COLUMN IF NOT EXISTS parentBomId INT;


-- 3️⃣ Populate parentBomId from old bomPositionId
UPDATE bomPosition
SET parentBomId = bomPositionId
WHERE parentBomId IS NULL;


-- 4️⃣ Apply NOT NULL constraint now that all values are correct
ALTER TABLE bomPosition
ALTER COLUMN parentBomId SET NOT NULL;


-- 5️⃣ Add foreign key for parent BOM
ALTER TABLE bomPosition
ADD CONSTRAINT fk_bomPosition_parentBom
FOREIGN KEY (parentBomId) REFERENCES bom(id) ON DELETE CASCADE;

-- 6️⃣ Add childBomId column (nullable for now)
ALTER TABLE bomPosition
ADD COLUMN IF NOT EXISTS childBomId INT;


-- 7️⃣ Add FK for child BOM
ALTER TABLE bomPosition
ADD CONSTRAINT fk_bomPosition_childBom
FOREIGN KEY (childBomId) REFERENCES bom(id);


-- 8️⃣ Drop old inventoryItemId (no longer used)
ALTER TABLE bomPosition
DROP CONSTRAINT IF EXISTS fkew6fvby7ekq8ppxm8hmepxn58;

ALTER TABLE bomPosition
DROP COLUMN IF EXISTS inventoryItemId;


-- 9️⃣ Add indexes for BOM tree traversal
CREATE INDEX IF NOT EXISTS idx_bomPosition_parentBom ON bomPosition(parentBomId);
CREATE INDEX IF NOT EXISTS idx_bomPosition_childBom ON bomPosition(childBomId);


-- 🔟 Cleanup: remove old FK constraint on bomPositionId if exists
ALTER TABLE bomPosition
DROP CONSTRAINT IF EXISTS fk93of9kyo1jtx37pdjcp1vtlmh;


-- 1️⃣1️⃣ Optional: Drop old bomPositionId column if no longer needed
ALTER TABLE bomPosition
DROP COLUMN IF EXISTS bomPositionId;
