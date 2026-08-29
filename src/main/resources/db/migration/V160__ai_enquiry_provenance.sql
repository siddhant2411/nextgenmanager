-- Provenance for enquiries raised by the AI Lead Agent, and the Gmail keys it dedupes on.
--
-- The agent reads inbound mail, extracts an RFQ and POSTs an enquiry. Two things the register
-- cannot answer without these columns:
--
-- 1. Which rows a machine wrote. An enquiry typed by sales and one extracted from a mail body by
--    a 4B model are not the same evidence, and a close-rate computed over both without being able
--    to separate them is a number nobody can defend. aiConfidence and aiScore are kept alongside
--    the flag so a bad batch can be found by its confidence band rather than by re-reading bodies.
--
-- 2. Whether a mail has already been filed. The agent's own database knows what it processed, but
--    it is a separate database -- restore it, re-point it, or run a second instance and the same
--    thread files a second enquiry against the same customer. gmailThreadId puts the dedupe key in
--    the same row as the enquiry it protects, which is the only place it cannot drift out of step.
--
-- Column names carry no underscores on purpose. This schema runs under
-- PhysicalNamingStrategyStandardImpl, so a JPA field maps to a column of exactly its own name --
-- opportunityName is stored as opportunityname, not opportunity_name. A snake_case column here
-- would simply never be found by the entity that declares it.
--
-- Every column is nullable and additive; existing rows read as not-AI once the flag is backfilled.

ALTER TABLE enquiry
    ADD COLUMN IF NOT EXISTS aiGenerated BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS aiConfidence NUMERIC(4, 3),
    ADD COLUMN IF NOT EXISTS aiScore INTEGER,
    ADD COLUMN IF NOT EXISTS aiModel VARCHAR(100),
    ADD COLUMN IF NOT EXISTS aiRequiresReview BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS gmailMessageId VARCHAR(255),
    ADD COLUMN IF NOT EXISTS gmailThreadId VARCHAR(255);

-- The message id is the agent's idempotency key: the same Gmail message must never produce a
-- second enquiry. Unique rather than plain, and partial so the 329 rows already in the register --
-- and every enquiry sales types by hand from here on -- stay exempt.
CREATE UNIQUE INDEX IF NOT EXISTS idx_enquiry_gmail_message
    ON enquiry (gmailMessageId) WHERE gmailMessageId IS NOT NULL;

-- Thread id is a lookup, not a key: a thread legitimately spans several enquiries when a customer
-- reuses one mail chain for successive RFQs.
CREATE INDEX IF NOT EXISTS idx_enquiry_gmail_thread
    ON enquiry (gmailThreadId) WHERE gmailThreadId IS NOT NULL;

-- The review desk asks exactly one question -- what is still waiting on a human -- so the index
-- only holds the rows that answer yes.
CREATE INDEX IF NOT EXISTS idx_enquiry_ai_review
    ON enquiry (aiRequiresReview) WHERE aiRequiresReview = TRUE;
