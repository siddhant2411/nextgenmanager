-- V74: Change BomPosition child reference from Bom to InventoryItem
-- Instead of childBomId -> bom, we now use childInventoryItemId -> inventoryitem

-- Step 1: Add the new column
ALTER TABLE bomPosition ADD COLUMN childInventoryItemId BIGINT;

-- Step 2: Migrate existing data — each childBom points to a Bom whose parentInventoryItemId is the item we want
UPDATE bomPosition bp
SET childInventoryItemId = (
    SELECT b.parentinventoryitemid
    FROM bom b
    WHERE b.id = bp.childBomId
)
WHERE bp.childBomId IS NOT NULL;

-- Step 3: Add FK constraint
ALTER TABLE bomPosition
    ADD CONSTRAINT fk_bomposition_child_inventory_item
    FOREIGN KEY (childInventoryItemId) REFERENCES inventoryitem(inventoryItemId);

-- Step 4: Drop old FK and column
ALTER TABLE bomPosition DROP CONSTRAINT IF EXISTS fk_bomposition_childbom;
ALTER TABLE bomPosition DROP CONSTRAINT IF EXISTS bomposition_childbomid_fkey;
ALTER TABLE bomPosition DROP COLUMN IF EXISTS childBomId;

-- Step 5: Create index on new column
CREATE INDEX idx_bomposition_child_inventory_item ON bomPosition(childInventoryItemId);
