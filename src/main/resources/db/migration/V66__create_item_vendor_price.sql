-- Item Vendor Price: links a vendor (contact) to an inventory item with a quoted price.
-- Supports two price types:
--   PURCHASE  — vendor supplies the finished item (feeds BUY cost in Make-or-Buy)
--   JOB_WORK  — vendor charges processing fee only (feeds SUBCONTRACT cost in Make-or-Buy)

CREATE TABLE itemVendorPrice (
    id                  BIGSERIAL PRIMARY KEY,
    inventoryItemId     INTEGER       NOT NULL,
    vendorId            INTEGER       NOT NULL,
    priceType           VARCHAR(20)   NOT NULL CHECK (priceType IN ('PURCHASE', 'JOB_WORK')),
    pricePerUnit        NUMERIC(14,4) NOT NULL,
    currency            VARCHAR(3)    NOT NULL DEFAULT 'INR',
    leadTimeDays        INTEGER,
    minimumOrderQuantity NUMERIC(12,3),
    validFrom           TIMESTAMP,
    validTo             TIMESTAMP,
    isPreferredVendor   BOOLEAN       NOT NULL DEFAULT FALSE,
    gstRegistered       BOOLEAN       NOT NULL DEFAULT TRUE,
    paymentTerms        VARCHAR(100),
    remarks             TEXT,
    creationDate        TIMESTAMP     NOT NULL DEFAULT NOW(),
    updatedDate         TIMESTAMP,
    deletedDate         TIMESTAMP,

    CONSTRAINT fk_ivp_item   FOREIGN KEY (inventoryItemId) REFERENCES inventoryItem(inventoryItemId),
    CONSTRAINT fk_ivp_vendor FOREIGN KEY (vendorId)        REFERENCES contact(id),
    CONSTRAINT uq_item_vendor_pricetype UNIQUE (inventoryItemId, vendorId, priceType)
);

CREATE INDEX idx_ivp_item        ON itemVendorPrice (inventoryItemId);
CREATE INDEX idx_ivp_vendor      ON itemVendorPrice (vendorId);
CREATE INDEX idx_ivp_preferred   ON itemVendorPrice (inventoryItemId, priceType, isPreferredVendor);
