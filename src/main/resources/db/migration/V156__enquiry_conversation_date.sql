-- Gives a conversation record a date of its own, separate from the row's audit timestamp.
--
-- EnquiryConversationRecord only ever had creationDate, which is when the row was written --
-- fine while sales type notes as they happen, useless the moment history is imported. PEC's
-- 2026 register carries 13 dated follow-up columns ("Mail done" / "Msg done" against a date in
-- the header row); loading those without a date field would stamp every one of ~106 rows with
-- the import timestamp and destroy the only thing that made them worth loading -- when the
-- chase actually happened.
--
-- Nullable on purpose: records typed into the UI can leave it to the creation timestamp, and
-- readers COALESCE the two. Backfilled from creationDate so existing rows answer date queries.

ALTER TABLE enquiryConversationRecord
    ADD COLUMN IF NOT EXISTS conversationDate DATE;

UPDATE enquiryConversationRecord
   SET conversationDate = CAST(creationDate AS DATE)
 WHERE conversationDate IS NULL
   AND creationDate IS NOT NULL;

-- The list endpoint counts and max()es these per enquiry on every page load.
CREATE INDEX IF NOT EXISTS idx_enq_conversation_enquiry
    ON enquiryConversationRecord (enquiry_conversation_id);

-- Filters added alongside: status and source are both now first-class query params.
CREATE INDEX IF NOT EXISTS idx_enquiry_status ON enquiry (status);
CREATE INDEX IF NOT EXISTS idx_enquiry_enqdate ON enquiry (enqDate);
