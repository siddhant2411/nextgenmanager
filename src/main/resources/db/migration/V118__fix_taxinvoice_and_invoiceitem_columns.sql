-- V118: Fix taxinvoice and invoiceitem tables.
-- V117 used CREATE TABLE IF NOT EXISTS which silently no-oped because both
-- tables already existed from the V1 baseline (with different/missing columns).

-- ============================================================
-- taxinvoice
-- ============================================================

-- Rename invoiceno -> invoiceNumber (entity field invoiceNumber -> PG column invoicenumber)
DO $$ BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.columns
               WHERE table_name='taxinvoice' AND column_name='invoiceno') THEN
        ALTER TABLE taxinvoice RENAME COLUMN invoiceno TO invoiceNumber;
    END IF;
END $$;

-- Rename totalamount -> totalPayableAmount (entity field totalPayableAmount)
DO $$ BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.columns
               WHERE table_name='taxinvoice' AND column_name='totalamount') THEN
        ALTER TABLE taxinvoice RENAME COLUMN totalamount TO totalPayableAmount;
    END IF;
END $$;

ALTER TABLE taxinvoice ADD COLUMN IF NOT EXISTS dueDate             DATE;
ALTER TABLE taxinvoice ADD COLUMN IF NOT EXISTS status              VARCHAR(30) DEFAULT 'DRAFT';
ALTER TABLE taxinvoice ADD COLUMN IF NOT EXISTS subTotal            NUMERIC(12,2);
ALTER TABLE taxinvoice ADD COLUMN IF NOT EXISTS taxableValue        NUMERIC(12,2);
ALTER TABLE taxinvoice ADD COLUMN IF NOT EXISTS cgstAmount          NUMERIC(12,2);
ALTER TABLE taxinvoice ADD COLUMN IF NOT EXISTS sgstAmount          NUMERIC(12,2);
ALTER TABLE taxinvoice ADD COLUMN IF NOT EXISTS igstAmount          NUMERIC(12,2);
ALTER TABLE taxinvoice ADD COLUMN IF NOT EXISTS totalPayableAmount  NUMERIC(12,2);
ALTER TABLE taxinvoice ADD COLUMN IF NOT EXISTS paidAmount          NUMERIC(12,2) DEFAULT 0;
ALTER TABLE taxinvoice ADD COLUMN IF NOT EXISTS creationDate        TIMESTAMP;
ALTER TABLE taxinvoice ADD COLUMN IF NOT EXISTS updatedDate         TIMESTAMP;
ALTER TABLE taxinvoice ADD COLUMN IF NOT EXISTS deletedDate         TIMESTAMP;

-- Constraints added after columns exist
DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'taxinvoice_invoicenumber_key') THEN
        ALTER TABLE taxinvoice ADD CONSTRAINT taxinvoice_invoicenumber_key UNIQUE (invoiceNumber);
    END IF;
END $$;

DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'taxinvoice_status_check') THEN
        ALTER TABLE taxinvoice ADD CONSTRAINT taxinvoice_status_check
            CHECK (status IN ('DRAFT','SENT','PAID','CANCELLED'));
    END IF;
END $$;

-- ============================================================
-- invoiceitem
-- ============================================================

-- Rename quantity -> qty (entity field qty)
DO $$ BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.columns
               WHERE table_name='invoiceitem' AND column_name='quantity') THEN
        ALTER TABLE invoiceitem RENAME COLUMN quantity TO qty;
    END IF;
END $$;

-- Rename inventoryitem_inventoryitemid -> inventoryitem_id (entity uses @JoinColumn(name="inventoryitem_id"))
DO $$ BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.columns
               WHERE table_name='invoiceitem' AND column_name='inventoryitem_inventoryitemid') THEN
        ALTER TABLE invoiceitem RENAME COLUMN inventoryitem_inventoryitemid TO inventoryitem_id;
    END IF;
END $$;

ALTER TABLE invoiceitem ADD COLUMN IF NOT EXISTS qty           NUMERIC(12,3);
ALTER TABLE invoiceitem ADD COLUMN IF NOT EXISTS pricePerUnit  NUMERIC(12,2);
ALTER TABLE invoiceitem ADD COLUMN IF NOT EXISTS cgstAmount    NUMERIC(12,2);
ALTER TABLE invoiceitem ADD COLUMN IF NOT EXISTS sgstAmount    NUMERIC(12,2);
ALTER TABLE invoiceitem ADD COLUMN IF NOT EXISTS igstAmount    NUMERIC(12,2);
ALTER TABLE invoiceitem ADD COLUMN IF NOT EXISTS totalAmount   NUMERIC(12,2);
