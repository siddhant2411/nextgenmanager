INSERT INTO productspecification (
    inventory_item_id,
    dimension,
    size,
    weight,
    basicmaterial,
    processtype,
    drawingnumber
)

SELECT
    inventoryitemid,
    dimension,
    size,
    weight,
    basicmaterial,
    processtype,
    drawingnumber
FROM inventoryitem