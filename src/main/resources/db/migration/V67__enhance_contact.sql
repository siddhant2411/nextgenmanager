-- Enhance Contact module to support Vendor/Customer categories,
-- GST compliance fields, MSME registration, and richer address/person details.

-- ── contact table ────────────────────────────────────────────────────────────

ALTER TABLE contact
    ADD COLUMN IF NOT EXISTS contactCode        VARCHAR(20),
    ADD COLUMN IF NOT EXISTS tradeName          VARCHAR(200),
    ADD COLUMN IF NOT EXISTS contactType        VARCHAR(20) NOT NULL DEFAULT 'VENDOR',
    ADD COLUMN IF NOT EXISTS gstType            VARCHAR(20) DEFAULT 'REGULAR',
    ADD COLUMN IF NOT EXISTS panNumber          VARCHAR(10),
    ADD COLUMN IF NOT EXISTS msmeRegistered     BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS msmeNumber         VARCHAR(30),
    ADD COLUMN IF NOT EXISTS defaultPaymentTerms VARCHAR(100),
    ADD COLUMN IF NOT EXISTS creditDays         INTEGER,
    ADD COLUMN IF NOT EXISTS currency           VARCHAR(3) DEFAULT 'INR',
    ADD COLUMN IF NOT EXISTS website            VARCHAR(255),
    ADD COLUMN IF NOT EXISTS phone              VARCHAR(20),
    ADD COLUMN IF NOT EXISTS email              VARCHAR(255);

-- Unique index on contactCode (populated via application logic on create)
CREATE UNIQUE INDEX IF NOT EXISTS idx_contact_code ON contact (contactCode) WHERE contactCode IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_contact_type ON contact (contactType);
CREATE INDEX IF NOT EXISTS idx_contact_gst  ON contact (gstNumber);

-- ── contact_address table ────────────────────────────────────────────────────

ALTER TABLE contact_address
    ADD COLUMN IF NOT EXISTS addressType VARCHAR(20) DEFAULT 'BILLING',
    ADD COLUMN IF NOT EXISTS isDefault   BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS city        VARCHAR(100),
    ADD COLUMN IF NOT EXISTS country     VARCHAR(100) DEFAULT 'India';

-- ── contact_person_detail table ──────────────────────────────────────────────

ALTER TABLE contact_person_detail
    ADD COLUMN IF NOT EXISTS designation    VARCHAR(100),
    ADD COLUMN IF NOT EXISTS department     VARCHAR(100),
    ADD COLUMN IF NOT EXISTS whatsappNumber VARCHAR(15),
    ADD COLUMN IF NOT EXISTS isPrimary      BOOLEAN NOT NULL DEFAULT FALSE;
