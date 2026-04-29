-- Goods Receipt Note (GRN) header table
CREATE TABLE IF NOT EXISTS goodsReceiptNote (
    id                  bigserial PRIMARY KEY,
    grnNumber           varchar(50) NOT NULL UNIQUE,
    grnDate             date NOT NULL,
    purchase_order_id   bigint REFERENCES purchaseorder(id),
    vendor_id           int REFERENCES contact(id),
    warehouse           varchar(100),
    status              varchar(20) NOT NULL DEFAULT 'SUBMITTED',
    totalAmount         float8 NOT NULL DEFAULT 0,
    remarks             text,
    createdBy           varchar(100),
    createdDate         timestamp DEFAULT now()
);

-- Goods Receipt Note line items
CREATE TABLE IF NOT EXISTS goodsReceiptItem (
    id                  bigserial PRIMARY KEY,
    grn_id              bigint NOT NULL REFERENCES goodsReceiptNote(id) ON DELETE CASCADE,
    inventory_item_id   int NOT NULL REFERENCES inventoryitem(inventoryitemid),
    orderedQty          float8 NOT NULL DEFAULT 0,
    receivedQty         float8 NOT NULL DEFAULT 0,
    acceptedQty         float8 NOT NULL DEFAULT 0,
    rejectedQty         float8 NOT NULL DEFAULT 0,
    rate                float8 NOT NULL DEFAULT 0,
    amount              float8 NOT NULL DEFAULT 0,
    batchNo             varchar(100),
    expiryDate          date,
    rejectionReason     text
);

CREATE INDEX IF NOT EXISTS idx_grn_po ON goodsReceiptNote(purchase_order_id);
CREATE INDEX IF NOT EXISTS idx_grn_vendor ON goodsReceiptNote(vendor_id);
CREATE INDEX IF NOT EXISTS idx_grn_item_grn ON goodsReceiptItem(grn_id);
CREATE INDEX IF NOT EXISTS idx_grn_item_item ON goodsReceiptItem(inventory_item_id);
