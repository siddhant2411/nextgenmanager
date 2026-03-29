CREATE TABLE productFinanceSettings(
    id SERIAL PRIMARY KEY,
    inventory_item_id INT4 NOT NULL REFERENCES inventoryitem(inventoryitemid),
    standardCost float8 NOT NULL DEFAULT 0,
    lastPurchaseCost float8  DEFAULT 0,
    sellingPrice float8 NOT NULL DEFAULT 0,
    profitMargin float8,
    minimumSellingPrice float8,
    costingMethod VARCHAR(128),
    taxCategory VARCHAR(128),
    currency VARCHAR(8),
    lastUpdatedOn timestamp(6) without time zone,
    expiryDate timestamp(6) without time zone
);

CREATE SEQUENCE productFinanceSettings_SEQ START 1 INCREMENT 50;
ALTER TABLE productFinanceSettings ALTER COLUMN id SET DEFAULT nextval('productFinanceSettings_SEQ');


