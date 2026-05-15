CREATE TABLE vendorInvoice (
    id                BIGSERIAL        PRIMARY KEY,
    invoiceNumber     VARCHAR(100)     NOT NULL UNIQUE,
    invoiceDate       DATE,
    purchase_order_id BIGINT           NOT NULL REFERENCES purchaseorder(id),
    vendor_id         INTEGER          REFERENCES contact(id),
    grn_id            BIGINT           REFERENCES goodsreceiptnote(id),
    status            VARCHAR(20)      NOT NULL DEFAULT 'DRAFT',
    subtotal          NUMERIC(14, 2)   NOT NULL DEFAULT 0,
    cgstAmount        NUMERIC(14, 2)   NOT NULL DEFAULT 0,
    sgstAmount        NUMERIC(14, 2)   NOT NULL DEFAULT 0,
    igstAmount        NUMERIC(14, 2)   NOT NULL DEFAULT 0,
    cessAmount        NUMERIC(14, 2)   NOT NULL DEFAULT 0,
    grandTotal        NUMERIC(14, 2)   NOT NULL DEFAULT 0,
    qtyMismatch       BOOLEAN          NOT NULL DEFAULT FALSE,
    amountMismatch    BOOLEAN          NOT NULL DEFAULT FALSE,
    remarks           TEXT,
    createdBy         VARCHAR(255),
    createdDate       TIMESTAMP,
    updatedDate       TIMESTAMP,
    deletedDate       TIMESTAMP,
    CONSTRAINT vendorinvoice_status_check CHECK (status IN ('DRAFT', 'POSTED', 'CANCELLED'))
);

CREATE TABLE vendorInvoiceItem (
    id           BIGSERIAL      PRIMARY KEY,
    invoice_id   BIGINT         NOT NULL REFERENCES vendorInvoice(id),
    item_id      INTEGER        REFERENCES inventoryitem(inventoryitemid),
    hsnCode      VARCHAR(10),
    uom          VARCHAR(20),
    invoicedQty  DOUBLE PRECISION NOT NULL DEFAULT 0,
    unitPrice    NUMERIC(14, 4) NOT NULL DEFAULT 0,
    taxableValue NUMERIC(14, 2) NOT NULL DEFAULT 0,
    cgstAmount   NUMERIC(14, 2) NOT NULL DEFAULT 0,
    sgstAmount   NUMERIC(14, 2) NOT NULL DEFAULT 0,
    igstAmount   NUMERIC(14, 2) NOT NULL DEFAULT 0,
    cessAmount   NUMERIC(14, 2) NOT NULL DEFAULT 0,
    lineTotal    NUMERIC(14, 2) NOT NULL DEFAULT 0
);

CREATE INDEX idx_vendorinvoice_po     ON vendorInvoice(purchase_order_id);
CREATE INDEX idx_vendorinvoice_vendor ON vendorInvoice(vendor_id);
CREATE INDEX idx_vendorinvoiceitem_invoice ON vendorInvoiceItem(invoice_id);
