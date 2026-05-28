-- ── Purchase Return / Debit Note ────────────────────────────────────────────
-- V126: debitNote (header) + debitNoteItem (lines)

CREATE TABLE debitNote (
    id               BIGSERIAL PRIMARY KEY,
    debitNoteNumber  VARCHAR(30)  NOT NULL UNIQUE,
    debitNoteDate    DATE         NOT NULL,
    vendor_id        INT          REFERENCES contact(id),
    purchase_order_id BIGINT      REFERENCES purchaseOrder(id),
    grn_id           BIGINT       REFERENCES goodsReceiptNote(id),
    returnReason     VARCHAR(30)  NOT NULL DEFAULT 'OTHER',
    status           VARCHAR(20)  NOT NULL DEFAULT 'DRAFT',
    remarks          TEXT,
    subtotal         NUMERIC(14,2) NOT NULL DEFAULT 0,
    totalGstAmount   NUMERIC(14,2) NOT NULL DEFAULT 0,
    totalAmount      NUMERIC(14,2) NOT NULL DEFAULT 0,
    createdBy        VARCHAR(100),
    createdDate      DATE         NOT NULL DEFAULT CURRENT_DATE,
    deletedDate      DATE
);

CREATE TABLE debitNoteItem (
    id              BIGSERIAL PRIMARY KEY,
    debit_note_id   BIGINT       NOT NULL REFERENCES debitNote(id) ON DELETE CASCADE,
    inventory_item_id INT        NOT NULL REFERENCES inventoryItem(inventoryItemId),
    lineNumber      INT          NOT NULL DEFAULT 1,
    returnedQty     NUMERIC(14,3) NOT NULL DEFAULT 0,
    rate            NUMERIC(14,2) NOT NULL DEFAULT 0,
    gstRate         NUMERIC(5,2)  NOT NULL DEFAULT 0,
    gstAmount       NUMERIC(14,2) NOT NULL DEFAULT 0,
    totalAmount     NUMERIC(14,2) NOT NULL DEFAULT 0,
    warehouseFrom   VARCHAR(100),
    remarks         VARCHAR(500)
);
