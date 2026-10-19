-- Production scheduling, phase 2: time between operations, and overlapping them.
--
-- queueTime:        minimum minutes an operation waits after its material arrives before it
--                   can start (staging, inspection, cooling). Wall-clock minutes. Any wait for
--                   the machine itself comes from the capacity ledger on top of this.
-- moveTime:         minutes to carry the output of this operation to the next one. Wall-clock.
-- transferQuantity: when set below the operation's quantity, output moves on in batches of
--                   this size, so the next operation can start before this one has finished.
--                   Null means the whole quantity moves at once.
ALTER TABLE routingOperation
    ADD COLUMN queueTime        NUMERIC(10, 2),
    ADD COLUMN moveTime         NUMERIC(10, 2),
    ADD COLUMN transferQuantity NUMERIC(12, 4);
