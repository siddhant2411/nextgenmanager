CREATE TABLE vendorPayment (
    id               BIGSERIAL     PRIMARY KEY,
    vendorInvoice_id BIGINT        NOT NULL REFERENCES vendorInvoice(id),
    paymentDate      DATE          NOT NULL,
    amount           NUMERIC(12,2) NOT NULL,
    paymentMode      VARCHAR(20)   NOT NULL
        CHECK (paymentMode IN ('CASH','CHEQUE','NEFT','RTGS','UPI','OTHER')),
    referenceNumber  VARCHAR(100),
    notes            VARCHAR(500),
    createdBy        VARCHAR(100),
    creationDate     TIMESTAMP     NOT NULL DEFAULT NOW(),
    updatedDate      TIMESTAMP     NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_vendorpayment_invoice ON vendorPayment(vendorInvoice_id);
