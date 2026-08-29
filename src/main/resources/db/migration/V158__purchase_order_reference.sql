-- An external reference on a purchase order: where this PO came from outside the ERP.
--
-- salesOrder has carried `reference` since it was written, and the 2026 sales-register import
-- leaned on it hard: it is what makes a re-run a top-up instead of a second copy of the year.
-- purchaseOrder never got the same column, and PEC's `Purchase Order List-2026.xlsx` is the
-- case that proves it is needed -- 156 POs and 29 job-work POs numbered 1..156 in a spreadsheet,
-- which become PO/2026-27/0001.. on the way in. Without somewhere to record "this row is PO 26
-- of the 2026 register", nothing ties the two numbering schemes together: a second import
-- duplicates the year, and no one can answer "which ERP PO is register PO 26?".
--
-- Deliberately free text rather than an FK. It carries a spreadsheet row today, and will carry
-- a vendor portal id, an email thread or a scanned document reference tomorrow. Unique it is
-- not -- an amended PO legitimately shares its source reference with the PO it revises.

ALTER TABLE purchaseOrder
    ADD COLUMN IF NOT EXISTS reference VARCHAR(200);

-- Importers and agents look POs up by reference and by number; both are point lookups over a
-- table that only grows, so both want an index.
CREATE INDEX IF NOT EXISTS idx_purchaseorder_reference ON purchaseOrder (reference);
CREATE INDEX IF NOT EXISTS idx_purchaseorder_number    ON purchaseOrder (purchaseOrderNumber);

-- The list endpoint filters on these three in combination, and orderDate is the natural sort.
CREATE INDEX IF NOT EXISTS idx_purchaseorder_orderdate ON purchaseOrder (orderDate);
CREATE INDEX IF NOT EXISTS idx_purchaseorder_vendor    ON purchaseOrder (vendor_id);
