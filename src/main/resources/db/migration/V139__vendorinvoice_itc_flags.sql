-- ── GST (Phase 2): Vendor Invoice ITC eligibility & reverse-charge flags ─────
-- Drives GSTR-3B section 4 (eligible ITC vs blocked credits per Sec 17(5)) and
-- the RCM split (3.1(d)). Existing rows are treated as eligible, non-RCM.
ALTER TABLE vendorInvoice
    ADD COLUMN itcEligible         BOOLEAN      NOT NULL DEFAULT TRUE,
    ADD COLUMN itcIneligibleReason VARCHAR(120),
    ADD COLUMN reverseCharge       BOOLEAN      NOT NULL DEFAULT FALSE;
