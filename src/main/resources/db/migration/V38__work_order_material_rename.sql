ALTER TABLE work_order_material RENAME TO workordermaterial;
ALTER TABLE workordermaterial RENAME COLUMN creation_date TO creationDate;
ALTER TABLE workordermaterial RENAME COLUMN updated_date TO updatedDate;
ALTER TABLE workordermaterial RENAME COLUMN deleted_date TO deletedDate;