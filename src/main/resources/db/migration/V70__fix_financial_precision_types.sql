-- V70: Fix financial field precision - change double precision to NUMERIC for monetary/quantity fields
-- Baseline tables were created with unquoted lowercase identifiers in PostgreSQL.

-- enquiredproducts: qty and priceperunit
ALTER TABLE IF EXISTS enquiredproducts
    ALTER COLUMN qty TYPE NUMERIC(10, 2) USING qty::NUMERIC(10, 2),
    ALTER COLUMN priceperunit TYPE NUMERIC(12, 2) USING priceperunit::NUMERIC(12, 2);

ALTER TABLE IF EXISTS enquiredproducts
    ALTER COLUMN qty SET DEFAULT 0,
    ALTER COLUMN priceperunit SET DEFAULT 0;

-- inventoryinstance: quantity, costperunit, sellpriceperunit
ALTER TABLE IF EXISTS inventoryinstance
    ALTER COLUMN quantity TYPE NUMERIC(15, 5) USING quantity::NUMERIC(15, 5),
    ALTER COLUMN costperunit TYPE NUMERIC(12, 2) USING costperunit::NUMERIC(12, 2),
    ALTER COLUMN sellpriceperunit TYPE NUMERIC(12, 2) USING sellpriceperunit::NUMERIC(12, 2);

ALTER TABLE IF EXISTS inventoryinstance
    ALTER COLUMN quantity SET DEFAULT 0,
    ALTER COLUMN costperunit SET DEFAULT 0,
    ALTER COLUMN sellpriceperunit SET DEFAULT 0;
