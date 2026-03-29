CREATE TABLE productspecification(
    id SERIAL PRIMARY KEY,
    inventory_item_id INT4 NOT NULL REFERENCES inventoryitem(inventoryitemid),
    dimension VARCHAR(100),
    size VARCHAR(100),
    weight  VARCHAR(100),
    basicMaterial  VARCHAR(100),
    processType VARCHAR(100),
    drawingNumber  VARCHAR(100)
)
