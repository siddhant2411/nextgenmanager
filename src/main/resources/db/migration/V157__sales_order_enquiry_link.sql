-- Links a sales order back to the enquiry it came from.
--
-- salesOrder already carried quotation_id, so the intended chain -- enquiry -> quotation -> order
-- -- was navigable in principle. In practice it is not enough. PEC's 2026 sales register books 63
-- orders of which only 3 name a quotation at all; the rest arrived by phone, WhatsApp or mail
-- against enquiries that were never formally quoted. Reached only through the quotation, those
-- orders leave the enquiry that produced the revenue unable to prove it -- and "which enquiries
-- actually turned into money?" is the whole reason the register exists.
--
-- Nullable: an order can legitimately have no enquiry behind it (a repeat customer who simply
-- sends a PO). Where a quotation IS present, the service keeps this column in step with
-- quotation.enquiry rather than letting the two disagree.

ALTER TABLE salesOrder
    ADD COLUMN IF NOT EXISTS enquiry_id BIGINT REFERENCES enquiry (id);

CREATE INDEX IF NOT EXISTS idx_salesorder_enquiry ON salesOrder (enquiry_id);

-- The reporting path runs enquiry -> orders, so quotation_id wants an index of its own too.
CREATE INDEX IF NOT EXISTS idx_salesorder_quotation ON salesOrder (quotation_id);

-- Backfill anything already linked through a quotation, so the new column is not a second
-- source of truth that starts out disagreeing with the first.
UPDATE salesOrder so
   SET enquiry_id = q.enquiry_id
  FROM quotation q
 WHERE so.quotation_id = q.id
   AND so.enquiry_id IS NULL
   AND q.enquiry_id IS NOT NULL;
