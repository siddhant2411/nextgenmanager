-- V78: Rename inventoryItemId to inventory_item_id to match EnquiredProducts and QuotationProducts entities
ALTER TABLE enquiredproducts RENAME COLUMN inventoryItemId TO inventory_item_id;
ALTER TABLE quotation_products RENAME COLUMN inventoryItemId TO inventory_item_id;
