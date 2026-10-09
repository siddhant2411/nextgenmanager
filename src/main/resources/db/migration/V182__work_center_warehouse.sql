-- A work centre says where work is done; nothing said where its stock lives.
--
-- Every stock movement a work order made -- reserving and consuming material, putting finished
-- goods on the shelf -- carried no warehouse and so fell to the default one. With a single site
-- that is correct. With two plants it means a job run entirely on Plant 2's machines draws from,
-- and produces into, Plant 1's store.
--
-- The link goes on the work centre rather than the work order because the plant is a property of
-- the machines, not something to be re-keyed on every order. Null keeps the old behaviour: the
-- default warehouse. Nothing changes for an installation that never sets it.

ALTER TABLE workCenter ADD COLUMN warehouse_id BIGINT;

ALTER TABLE workCenter ADD CONSTRAINT fk_workcenter_warehouse
    FOREIGN KEY (warehouse_id) REFERENCES warehouse (id);

CREATE INDEX idx_workcenter_warehouse ON workCenter (warehouse_id);

COMMENT ON COLUMN workCenter.warehouse_id IS
    'The store this work centre draws material from and produces into. Null means the default warehouse.';
