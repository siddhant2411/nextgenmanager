-- Phase J step 2 — the delivery note ships a closed packing slip.
--
-- Step 1 recorded what went into which box, and nothing downstream looked at it: a pick could be
-- half-packed, or packed with a failed inspection outstanding, and still leave on a delivery note.
-- The service now refuses to ship a pick whose slip is not closed. This records the other half —
-- which delivery note the slip left on — the same way V168 did for the pick.
--
-- Deliberately not a NOT NULL or a status: a closed slip with no delivery note is the normal
-- state of goods packed and waiting for a lorry.

ALTER TABLE packingslip ADD COLUMN deliverynote_id BIGINT;

ALTER TABLE packingslip ADD CONSTRAINT fk_packingslip_deliverynote
    FOREIGN KEY (deliverynote_id) REFERENCES deliverynote (id);

-- One slip per delivery note, because a delivery note ships one pick and a pick has one live slip.
CREATE UNIQUE INDEX ux_packingslip_deliverynote
    ON packingslip (deliverynote_id)
    WHERE deliverynote_id IS NOT NULL;

-- Only a closed slip can have left the building.
ALTER TABLE packingslip ADD CONSTRAINT ck_packingslip_shipped_is_closed
    CHECK (deliverynote_id IS NULL OR status = 'CLOSED');

COMMENT ON COLUMN packingslip.deliverynote_id IS
    'The delivery note this slip shipped on. Set once, at dispatch. Null on a closed slip means
     packed and waiting to ship.';
