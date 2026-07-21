-- Per-BOM blanket manufacturing overhead %.
--
-- Replaces the per-work-center conversion overhead as the costing mechanism. A single rate is
-- applied at the BOM rollup to the total manufacturing cost base — material + in-house conversion
-- (machine + labour) + consumables/additional + fixed-rate operations — EXCLUDING subcontracted
-- operations (whose vendor price already carries the vendor's overhead). Nullable; null = 0%.
--
-- The legacy workCenter.overheadPercentage column is left in place but no longer drives costing.
ALTER TABLE bom
    ADD COLUMN IF NOT EXISTS overheadPercentage NUMERIC(6,3);
