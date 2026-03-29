CREATE SEQUENCE productInventorySettings_SEQ START 1 INCREMENT 50;
ALTER TABLE productInventorySettings ALTER COLUMN id SET DEFAULT nextval('productInventorySettings_SEQ');
