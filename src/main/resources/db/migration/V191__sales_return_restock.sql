-- A credit note line now says what happened to the goods (back to stock, into quarantine, or none
-- came back at all) and the rate it valued them at. Both are what a cancelled note needs to take
-- back out exactly what it put in. Null on lines confirmed before this: those restocked as good.
ALTER TABLE salesCreditNoteItem ADD COLUMN goodsCondition VARCHAR(20);
ALTER TABLE salesCreditNoteItem ADD COLUMN restockRate NUMERIC(14,2);

-- The units a sales return created, so cancelling the note can find them again.
ALTER TABLE inventoryInstance ADD COLUMN sales_credit_note_item_id BIGINT;
CREATE INDEX idx_invinst_credit_note_item ON inventoryInstance(sales_credit_note_item_id);
