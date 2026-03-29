-- Add lastQuotedDate to itemVendorPrice and create price history table.

ALTER TABLE itemVendorPrice
    ADD COLUMN IF NOT EXISTS lastQuotedDate TIMESTAMP;

CREATE TABLE itemVendorPriceHistory (
    id                BIGSERIAL PRIMARY KEY,
    itemVendorPriceId BIGINT        NOT NULL,
    oldPrice          NUMERIC(14,4),
    newPrice          NUMERIC(14,4) NOT NULL,
    changedDate       TIMESTAMP     NOT NULL DEFAULT NOW(),
    changedBy         VARCHAR(100),
    remarks           TEXT,

    CONSTRAINT fk_ivph_price FOREIGN KEY (itemVendorPriceId) REFERENCES itemVendorPrice(id)
);

CREATE INDEX idx_ivph_price ON itemVendorPriceHistory (itemVendorPriceId);
