-- A purchase order can be delivered to one of our own plants.
--
-- Until now an order was delivered either to the company's registered address or to an address
-- picked from another contact (shipToAddressId). A company with more than one plant had no way to
-- say which one, short of keeping itself as a contact. The plants already exist as warehouses,
-- each with its own address, so the order points at one.
--
-- At most one of shipToWarehouseId and shipToAddressId is set; neither means the registered address.

ALTER TABLE purchaseOrder ADD COLUMN IF NOT EXISTS shipToWarehouseId BIGINT REFERENCES warehouse (id);
