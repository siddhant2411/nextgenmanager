-- The old dead revision counter — never read by any backend code — is fully replaced by
-- itemRevision now that every item has been backfilled onto it (V172).
ALTER TABLE inventoryItem DROP COLUMN revision;
