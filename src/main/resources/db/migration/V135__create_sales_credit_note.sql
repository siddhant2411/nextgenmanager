-- ── Sales Return / Credit Note ──────────────────────────────────────────────
-- V135: salesCreditNote (header) + salesCreditNoteItem (lines)
-- Sales-side mirror of debitNote (V126). Confirming a credit note auto-posts a
-- CREDIT_NOTE voucher that reverses the original sales invoice.

CREATE TABLE salesCreditNote (
    id               BIGSERIAL PRIMARY KEY,
    creditNoteNumber VARCHAR(30)  NOT NULL UNIQUE,
    creditNoteDate   DATE         NOT NULL,
    customer_id      INT          REFERENCES contact(id),
    tax_invoice_id   BIGINT       REFERENCES taxInvoice(id),
    reason           VARCHAR(40),
    status           VARCHAR(20)  NOT NULL DEFAULT 'DRAFT',
    remarks          TEXT,
    subtotal         NUMERIC(14,2) NOT NULL DEFAULT 0,
    totalGstAmount   NUMERIC(14,2) NOT NULL DEFAULT 0,
    totalAmount      NUMERIC(14,2) NOT NULL DEFAULT 0,
    createdBy        VARCHAR(100),
    createdDate      DATE         NOT NULL DEFAULT CURRENT_DATE,
    deletedDate      DATE
);

CREATE TABLE salesCreditNoteItem (
    id                  BIGSERIAL PRIMARY KEY,
    sales_credit_note_id BIGINT      NOT NULL REFERENCES salesCreditNote(id) ON DELETE CASCADE,
    inventory_item_id   INT          NOT NULL REFERENCES inventoryItem(inventoryItemId),
    lineNumber          INT          NOT NULL DEFAULT 1,
    returnedQty         NUMERIC(14,3) NOT NULL DEFAULT 0,
    rate                NUMERIC(14,2) NOT NULL DEFAULT 0,
    gstRate             NUMERIC(5,2)  NOT NULL DEFAULT 0,
    gstAmount           NUMERIC(14,2) NOT NULL DEFAULT 0,
    totalAmount         NUMERIC(14,2) NOT NULL DEFAULT 0,
    warehouseTo         VARCHAR(100),
    remarks             VARCHAR(500)
);

CREATE INDEX idx_salescreditnote_customer ON salesCreditNote(customer_id);
CREATE INDEX idx_salescreditnote_invoice  ON salesCreditNote(tax_invoice_id);
