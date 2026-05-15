-- V117: Create taxInvoice and invoiceitem tables for the customer invoice stack.
-- TaxInvoice.java uses @Table(name="taxInvoice") → PG table: taxinvoice
-- InvoiceItem.java uses no @Table → PG table: invoiceitem

CREATE TABLE IF NOT EXISTS taxinvoice (
    id                  BIGSERIAL PRIMARY KEY,
    invoiceNumber       VARCHAR(50) UNIQUE NOT NULL,
    salesorder_id       BIGINT REFERENCES salesorder(id),
    invoiceDate         DATE NOT NULL,
    dueDate             DATE,
    status              VARCHAR(30) DEFAULT 'DRAFT',
    subTotal            NUMERIC(12, 2),
    taxableValue        NUMERIC(12, 2),
    cgstAmount          NUMERIC(12, 2),
    sgstAmount          NUMERIC(12, 2),
    igstAmount          NUMERIC(12, 2),
    totalPayableAmount  NUMERIC(12, 2),
    paidAmount          NUMERIC(12, 2) DEFAULT 0,
    creationDate        TIMESTAMP,
    updatedDate         TIMESTAMP,
    deletedDate         TIMESTAMP,
    CONSTRAINT taxinvoice_status_check CHECK (status IN ('DRAFT','SENT','PAID','CANCELLED'))
);

CREATE TABLE IF NOT EXISTS invoiceitem (
    id                  BIGSERIAL PRIMARY KEY,
    taxinvoice_id       BIGINT REFERENCES taxinvoice(id),
    inventoryitem_id    INTEGER,
    qty                 NUMERIC(12, 3),
    pricePerUnit        NUMERIC(12, 2),
    cgstAmount          NUMERIC(12, 2),
    sgstAmount          NUMERIC(12, 2),
    igstAmount          NUMERIC(12, 2),
    totalAmount         NUMERIC(12, 2)
);
