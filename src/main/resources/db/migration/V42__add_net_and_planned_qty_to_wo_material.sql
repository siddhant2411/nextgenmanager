ALTER TABLE workordermaterial
ADD COLUMN netrequiredquantity NUMERIC(15,5);

ALTER TABLE workordermaterial
ADD COLUMN plannedrequiredquantity NUMERIC(15,5);

ALTER TABLE workordermaterial
ADD COLUMN scrappercent NUMERIC(8,4);

-- Migrate old data:
-- existing requiredquantity becomes planned quantity

UPDATE workordermaterial
SET plannedrequiredquantity = requiredquantity,
    netrequiredquantity = requiredquantity;

-- Make new columns NOT NULL after migration
ALTER TABLE workordermaterial
ALTER COLUMN netrequiredquantity SET NOT NULL;

ALTER TABLE workordermaterial
ALTER COLUMN plannedrequiredquantity SET NOT NULL;

-- Optional: drop old column
ALTER TABLE workordermaterial
DROP COLUMN requiredquantity;
