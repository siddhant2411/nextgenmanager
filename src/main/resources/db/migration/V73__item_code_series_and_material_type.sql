-- V73: Item Code Series (structured auto-numbering) + Material Type + Process Type lookup tables

-- ── Item Code Series ──────────────────────────────────────────────────────────
-- Stores named numbering series for auto item code generation.
-- e.g. series "FBTM" with prefix "FBTM", separator "-", padding 4 → FBTM-0001
CREATE TABLE itemCodeSeries (
    id           BIGSERIAL    PRIMARY KEY,
    seriesCode   VARCHAR(20)  NOT NULL UNIQUE,   -- short key used to reference the series
    description  VARCHAR(255),                   -- human-readable label
    prefix       VARCHAR(20)  NOT NULL,           -- code prefix (e.g. "FBTM", "RM", "FG")
    separator    VARCHAR(5)   NOT NULL DEFAULT '-',
    padding      INT          NOT NULL DEFAULT 4, -- zero-padded width of sequence number
    lastNumber   INT          NOT NULL DEFAULT 0, -- last consumed sequence value
    isActive     BOOLEAN      NOT NULL DEFAULT TRUE,
    creationDate TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    updatedDate  TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    deletedDate  TIMESTAMP    NULL
);

-- Seed common series
INSERT INTO itemCodeSeries (seriesCode, description, prefix, separator, padding, lastNumber, isActive) VALUES
  ('GEN',  'General Items',       'GEN',  '-', 4, 0, TRUE),
  ('RM',   'Raw Materials',       'RM',   '-', 4, 0, TRUE),
  ('FG',   'Finished Goods',      'FG',   '-', 4, 0, TRUE),
  ('SF',   'Semi-Finished',       'SF',   '-', 4, 0, TRUE),
  ('CONS', 'Consumables',         'CONS', '-', 4, 0, TRUE),
  ('PUR',  'Purchased Items',     'PUR',  '-', 4, 0, TRUE);

-- ── Material Type ──────────────────────────────────────────────────────────────
-- Dynamic lookup list for basicMaterial field on ProductSpecification.
CREATE TABLE materialType (
    id           BIGSERIAL    PRIMARY KEY,
    name         VARCHAR(100) NOT NULL UNIQUE,
    isActive     BOOLEAN      NOT NULL DEFAULT TRUE,
    creationDate TIMESTAMP    DEFAULT CURRENT_TIMESTAMP
);

-- Migrate existing distinct values from productSpecification.basicMaterial
INSERT INTO materialType (name)
SELECT DISTINCT TRIM(basicMaterial)
FROM productSpecification
WHERE basicMaterial IS NOT NULL AND TRIM(basicMaterial) <> ''
ON CONFLICT (name) DO NOTHING;

-- Seed common engineering materials (skipped if already exist from migration above)
INSERT INTO materialType (name) VALUES
  ('Carbon Steel'),
  ('Stainless Steel SS304'),
  ('Stainless Steel SS316'),
  ('Stainless Steel SS316L'),
  ('Mild Steel'),
  ('Cast Iron'),
  ('Aluminium'),
  ('Brass'),
  ('Bronze'),
  ('Copper'),
  ('Polypropylene (PP)'),
  ('PTFE'),
  ('Rubber'),
  ('Plastic')
ON CONFLICT (name) DO NOTHING;

-- ── Process Type ───────────────────────────────────────────────────────────────
-- Dynamic lookup list for processType field on ProductSpecification.
-- Describes the primary manufacturing process for the item.
CREATE TABLE processType (
    id           BIGSERIAL    PRIMARY KEY,
    name         VARCHAR(100) NOT NULL UNIQUE,
    isActive     BOOLEAN      NOT NULL DEFAULT TRUE,
    creationDate TIMESTAMP    DEFAULT CURRENT_TIMESTAMP
);

-- Migrate existing distinct values from productSpecification.processType
INSERT INTO processType (name)
SELECT DISTINCT TRIM(processType)
FROM productSpecification
WHERE processType IS NOT NULL AND TRIM(processType) <> ''
ON CONFLICT (name) DO NOTHING;

-- Seed common manufacturing processes
INSERT INTO processType (name) VALUES
  ('CNC Machining'),
  ('CNC Turning'),
  ('CNC Milling'),
  ('Welding'),
  ('Casting'),
  ('Forging'),
  ('Sheet Metal Fabrication'),
  ('Plasma Cutting'),
  ('Laser Cutting'),
  ('Injection Moulding'),
  ('Heat Treatment'),
  ('Surface Finishing'),
  ('Assembly'),
  ('Bought Out')
ON CONFLICT (name) DO NOTHING;
