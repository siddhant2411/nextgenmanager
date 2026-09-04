-- Phase I step 2 — the delivery note consumes a confirmed pick.
--
-- V167 recorded the physical act of picking but stopped short of the delivery note, which still
-- chose its own instances inline while it was also costing them, writing the ledger and
-- recalculating the sales order. That left two paths able to consume the same stock: the pick
-- allocated instances, and then a delivery note allocated its own, FIFO, ignoring them.
--
-- This closes that. A delivery note raised against a pick consumes exactly the units the picker
-- took off the shelf, and the pick is marked DISPATCHED so it cannot be consumed twice. The
-- inline path stays for counter sales and sample dispatches, which genuinely have no pick — but
-- it is now a named bypass rather than the default.

ALTER TABLE picklist ADD COLUMN deliverynote_id BIGINT;

ALTER TABLE picklist ADD CONSTRAINT fk_picklist_deliverynote
    FOREIGN KEY (deliverynote_id) REFERENCES deliverynote (id);

-- One delivery note per pick. The unique index is what actually stops a second delivery note
-- consuming the same picked units when two dispatchers click at once; the service check only
-- makes the failure legible.
CREATE UNIQUE INDEX ux_picklist_deliverynote
    ON picklist (deliverynote_id)
    WHERE deliverynote_id IS NOT NULL;

COMMENT ON COLUMN picklist.deliverynote_id IS
    'The delivery note that consumed this pick. Set once, at dispatch; a pick with a delivery
     note is spent.';

-- DISPATCHED joins the lifecycle: PICKED means the units are allocated and waiting, DISPATCHED
-- means a delivery note has taken them out of stock.
--
-- V167 declared the status check inline, so its name was chosen by Postgres. Dropping it by the
-- name we expect would silently do nothing if that guess were wrong, and the first DISPATCHED
-- pick would then be rejected by a constraint nobody could see — so find it by what it checks.
DO $$
DECLARE constraint_name TEXT;
BEGIN
    SELECT con.conname INTO constraint_name
    FROM pg_constraint con
    JOIN pg_class rel ON rel.oid = con.conrelid
    WHERE rel.relname = 'picklist'
      AND con.contype = 'c'
      AND pg_get_constraintdef(con.oid) LIKE '%RELEASED%';

    IF constraint_name IS NOT NULL THEN
        EXECUTE format('ALTER TABLE picklist DROP CONSTRAINT %I', constraint_name);
    END IF;
END $$;

ALTER TABLE picklist ADD CONSTRAINT picklist_status_check
    CHECK (status IN ('DRAFT','RELEASED','PICKED','DISPATCHED','CANCELLED'));

-- A pick is only spent once it has been dispatched, and only a dispatched pick has a note.
ALTER TABLE picklist ADD CONSTRAINT ck_picklist_dispatched_has_note
    CHECK ((status = 'DISPATCHED') = (deliverynote_id IS NOT NULL));

COMMENT ON COLUMN picklist.status IS
    'DRAFT is being prepared. RELEASED is on the floor to be picked. PICKED has specific instances
     allocated and is ready for a delivery note. DISPATCHED means a delivery note has consumed
     them. CANCELLED releases any allocation.';
