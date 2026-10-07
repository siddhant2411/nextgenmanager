-- Document branding.
--
-- One row holding how the company wants its printed documents headed: plain text (NONE), a logo
-- beside the company text (LOGO), a full-width letterhead image in place of the text header
-- (LETTERHEAD), or a blank band left for paper that already carries a printed letterhead
-- (PREPRINTED).
--
-- The images live here rather than in object storage because every PDF render needs them: they are
-- small once the upload has been scaled down, they stay with the rest of the company's settings,
-- and a document can still be printed when the file store is unreachable. They are kept off
-- company_details so that the many places which read the company row do not drag the image bytes
-- along with it.
--
-- The logo and the letterhead are stored separately so that switching mode does not throw away the
-- other image.

CREATE TABLE document_branding (
    id                    BIGSERIAL PRIMARY KEY,
    mode                  VARCHAR(20) NOT NULL DEFAULT 'NONE',
    logoImage             BYTEA,
    logoContentType       VARCHAR(30),
    logoWidthPx           INTEGER,
    logoHeightPx          INTEGER,
    letterheadImage       BYTEA,
    letterheadContentType VARCHAR(30),
    letterheadWidthPx     INTEGER,
    letterheadHeightPx    INTEGER,
    creationDate          TIMESTAMP,
    updatedDate           TIMESTAMP,
    CONSTRAINT chk_document_branding_mode CHECK (mode IN ('NONE', 'LOGO', 'LETTERHEAD', 'PREPRINTED'))
);
