-- V71: Rename opaque abbreviations in quotation table to readable names
ALTER TABLE quotation
    RENAME COLUMN pandfcharges TO "packagingAndForwardingCharges";

ALTER TABLE quotation
    RENAME COLUMN "pandfchargesPercentage" TO "packagingAndForwardingChargesPercentage";
