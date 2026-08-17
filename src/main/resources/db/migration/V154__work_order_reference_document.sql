-- Free-text reference for manually raised work orders.
--
-- The work order form has always had a reference field that becomes editable when the source is
-- MANUAL, and it has never been possible to save what was typed into it: no column, no entity
-- field, and no property on the request DTO, so the value was quietly dropped by Jackson on the
-- way in. The field looked like it worked right up until the page was reloaded.
--
-- Only MANUAL uses it. A SALES_ORDER or PARENT_WORK_ORDER source is referenced through the
-- salesOrderId / parentWorkOrderId relations, which point at real records; a manual order has
-- nothing to point at, so its reference is whatever the operator types — a job card, an email,
-- a customer's PO number. The service clears the column when the source changes away from
-- MANUAL, so a stale job-card number can never sit beside a real sales order link.
--
-- Nullable with no backfill: every existing work order genuinely has no stored reference, and
-- inventing one would be worse than leaving it blank.

ALTER TABLE workOrder
    ADD COLUMN IF NOT EXISTS referenceDocument VARCHAR(255);
