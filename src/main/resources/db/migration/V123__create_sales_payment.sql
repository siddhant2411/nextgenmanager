CREATE TABLE salespayment (
    id             BIGSERIAL PRIMARY KEY,
    salesorder_id  BIGINT       NOT NULL REFERENCES salesorder(id),
    paymentDate    DATE         NOT NULL,
    amount         NUMERIC(12,2) NOT NULL,
    paymentMode    VARCHAR(20)  NOT NULL
        CHECK (paymentMode IN ('CASH','CHEQUE','NEFT','RTGS','UPI','OTHER')),
    referenceNumber VARCHAR(100),
    notes          VARCHAR(500),
    creationDate   TIMESTAMP    NOT NULL DEFAULT NOW(),
    updatedDate    TIMESTAMP    NOT NULL DEFAULT NOW()
);
