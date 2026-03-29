CREATE TABLE productInventorySettings(
    id SERIAL PRIMARY KEY,
    inventory_item_id INT4 NOT NULL REFERENCES inventoryitem(inventoryitemid),
    reorderLevel float8 NOT NULL DEFAULT 0,
    minStock float8 NOT NULL DEFAULT 0,
    maxStock float8 NOT NULL DEFAULT 0,
    leadTime  float8,
    isBatchTracked  bool,
    isSerialTracked bool,
    purchased  bool,
    manufactured bool,
    availableQuantity float8 NOT NULL DEFAULT 0,
    orderedQuantity float8 NOT NULL DEFAULT 0
);

INSERT INTO productInventorySettings (
    inventory_item_id,
    reorderLevel,
    minStock,
    maxStock,
    leadTime,
    isBatchTracked,
    isSerialTracked,
    purchased,
    manufactured,
    availableQuantity,
    orderedQuantity
)
SELECT
    inventoryitemid,
    COALESCE(NULLIF(reorderLevel, '')::float8, 0),
    COALESCE(NULLIF(minStock, '')::float8, 0),
    COALESCE(NULLIF(maxStock, '')::float8, 0),
    COALESCE(NULLIF(leadTime, '')::float8, 0),
    isBatchTracked,
    isSerialTracked,
    purchased,
    manufactured,
    availableQuantity,
    orderedQuantity
FROM inventoryitem;

ALTER TABLE INVENTORYITEM
DROP COLUMN reorderLevel,
DROP COLUMN minStock,
DROP COLUMN maxStock,
DROP COLUMN leadTime,
DROP COLUMN isBatchTracked,
DROP COLUMN purchased,
DROP COLUMN manufactured,
DROP COLUMN availableQuantity,
DROP COLUMN orderedQuantity,
DROP COLUMN isSerialTracked;
