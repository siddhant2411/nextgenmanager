-- V70: Fix financial field precision - change double precision to NUMERIC for monetary/quantity fields
-- EnquiredProducts: qty and pricePerUnit
ALTER TABLE "enquiredProducts"
    ALTER COLUMN qty TYPE NUMERIC(10, 2) USING qty::NUMERIC(10, 2),
    ALTER COLUMN "pricePerUnit" TYPE NUMERIC(12, 2) USING "pricePerUnit"::NUMERIC(12, 2);

ALTER TABLE "enquiredProducts"
    ALTER COLUMN qty SET DEFAULT 0,
    ALTER COLUMN "pricePerUnit" SET DEFAULT 0;

-- inventoryInstance: quantity, costPerUnit, sellPricePerUnit
ALTER TABLE "inventoryInstance"
    ALTER COLUMN quantity TYPE NUMERIC(15, 5) USING quantity::NUMERIC(15, 5),
    ALTER COLUMN "costPerUnit" TYPE NUMERIC(12, 2) USING "costPerUnit"::NUMERIC(12, 2),
    ALTER COLUMN "sellPricePerUnit" TYPE NUMERIC(12, 2) USING "sellPricePerUnit"::NUMERIC(12, 2);

ALTER TABLE "inventoryInstance"
    ALTER COLUMN quantity SET DEFAULT 0,
    ALTER COLUMN "costPerUnit" SET DEFAULT 0,
    ALTER COLUMN "sellPricePerUnit" SET DEFAULT 0;
