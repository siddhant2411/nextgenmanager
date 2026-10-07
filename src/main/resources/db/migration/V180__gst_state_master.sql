-- GST state codes as a table, and every stored state code a foreign key to it.
--
-- The codes were only ever a Java enum. The database held a state as whatever had been typed: a
-- 2-digit code on a purchase order, a name and a 5-character "code" on a sales order, nothing at
-- all on most contacts. Nothing stopped "GJ" or "Gujrat", and no report could join a document to
-- its state.
--
-- The list is fixed by the GST law and is not user data, so it is seeded here and has no screen.
-- It is the same list as the GstState enum, and GstStateTest fails if the two drift apart. A new
-- state or union territory is a new migration plus a new enum constant.

CREATE TABLE IF NOT EXISTS gstState (
    code VARCHAR(2)   PRIMARY KEY,
    name VARCHAR(100) NOT NULL UNIQUE
);

INSERT INTO gstState (code, name) VALUES
    ('01', 'Jammu & Kashmir'),
    ('02', 'Himachal Pradesh'),
    ('03', 'Punjab'),
    ('04', 'Chandigarh'),
    ('05', 'Uttarakhand'),
    ('06', 'Haryana'),
    ('07', 'Delhi'),
    ('08', 'Rajasthan'),
    ('09', 'Uttar Pradesh'),
    ('10', 'Bihar'),
    ('11', 'Sikkim'),
    ('12', 'Arunachal Pradesh'),
    ('13', 'Nagaland'),
    ('14', 'Manipur'),
    ('15', 'Mizoram'),
    ('16', 'Tripura'),
    ('17', 'Meghalaya'),
    ('18', 'Assam'),
    ('19', 'West Bengal'),
    ('20', 'Jharkhand'),
    ('21', 'Odisha'),
    ('22', 'Chhattisgarh'),
    ('23', 'Madhya Pradesh'),
    ('24', 'Gujarat'),
    ('25', 'Daman & Diu (pre-2020)'),
    ('26', 'Dadra & Nagar Haveli and Daman & Diu'),
    ('27', 'Maharashtra'),
    ('28', 'Andhra Pradesh (pre-2014)'),
    ('29', 'Karnataka'),
    ('30', 'Goa'),
    ('31', 'Lakshadweep'),
    ('32', 'Kerala'),
    ('33', 'Tamil Nadu'),
    ('34', 'Puducherry'),
    ('35', 'Andaman & Nicobar Islands'),
    ('36', 'Telangana'),
    ('37', 'Andhra Pradesh'),
    ('38', 'Ladakh'),
    ('97', 'Other Territory'),
    ('99', 'Centre Jurisdiction')
ON CONFLICT (code) DO NOTHING;

-- ── Blank is not a state ─────────────────────────────────────────────────────
-- The forms sent '' for an empty field, and '' cannot reference anything.

UPDATE contact         SET stateCode              = NULL WHERE btrim(stateCode) = '';
UPDATE company_details SET stateCode              = NULL WHERE btrim(stateCode) = '';
UPDATE purchaseOrder   SET placeOfSupply          = NULL WHERE btrim(placeOfSupply) = '';
UPDATE salesOrder      SET shipToStateCode        = NULL WHERE btrim(shipToStateCode) = '';
UPDATE salesOrder      SET placeOfSupplyStateCode = NULL WHERE btrim(placeOfSupplyStateCode) = '';
UPDATE taxInvoice      SET billToStateCode        = NULL WHERE btrim(billToStateCode) = '';
UPDATE taxInvoice      SET shipToStateCode        = NULL WHERE btrim(shipToStateCode) = '';

-- ── Fill in what can be known ────────────────────────────────────────────────
-- Nothing ever wrote contact.stateCode or company_details.stateCode; both were worked out from the
-- GSTIN on every read. Only empty values are filled; nothing already stored is overwritten.

-- A registered party's state is the first two digits of its GSTIN.
UPDATE contact c SET stateCode = g.code
FROM gstState g
WHERE c.stateCode IS NULL AND g.code = left(btrim(c.gstNumber), 2);

UPDATE company_details c SET stateCode = g.code
FROM gstState g
WHERE c.stateCode IS NULL AND g.code = left(btrim(c.gstNumber), 2);

-- An unregistered party has only the state typed on its address: the default address first.
-- Names are compared the way GstState.fromName does: case, spacing and "&" / "and" ignored.
UPDATE contact c SET stateCode = m.code
FROM (
    SELECT DISTINCT ON (a.contact_id) a.contact_id, g.code
    FROM contact_address a
    JOIN gstState g
      ON regexp_replace(replace(lower(g.name), '&', 'and'), '[^a-z]', '', 'g')
       = regexp_replace(replace(lower(a.state), '&', 'and'), '[^a-z]', '', 'g')
    ORDER BY a.contact_id, a.isDefault DESC, a.id
) m
WHERE c.stateCode IS NULL AND m.contact_id = c.id;

UPDATE company_details c SET stateCode = g.code
FROM gstState g
WHERE c.stateCode IS NULL
  AND regexp_replace(replace(lower(g.name), '&', 'and'), '[^a-z]', '', 'g')
    = regexp_replace(replace(lower(c.state), '&', 'and'), '[^a-z]', '', 'g');

-- A sales order kept the place of supply as a typed name with an optional code beside it.
UPDATE salesOrder s SET placeOfSupplyStateCode = g.code
FROM gstState g
WHERE s.placeOfSupplyStateCode IS NULL
  AND regexp_replace(replace(lower(g.name), '&', 'and'), '[^a-z]', '', 'g')
    = regexp_replace(replace(lower(s.placeOfSupply), '&', 'and'), '[^a-z]', '', 'g');

UPDATE salesOrder s SET placeOfSupply = g.name
FROM gstState g
WHERE g.code = s.placeOfSupplyStateCode;

-- ── Foreign keys ─────────────────────────────────────────────────────────────
-- NOT VALID: enforced for every row written from now on, not checked against rows already there.
-- A code typed by hand years ago that is not a state ("GJ") stays where it is rather than being
-- thrown away or stopping the deploy. To see what is left to clean up, for each table:
--
--   SELECT id, stateCode FROM contact WHERE stateCode NOT IN (SELECT code FROM gstState);
--
-- and once a table is clean:  ALTER TABLE contact VALIDATE CONSTRAINT fk_contact_gststate;

ALTER TABLE contact
    ADD CONSTRAINT fk_contact_gststate
    FOREIGN KEY (stateCode) REFERENCES gstState (code) NOT VALID;

ALTER TABLE company_details
    ADD CONSTRAINT fk_company_details_gststate
    FOREIGN KEY (stateCode) REFERENCES gstState (code) NOT VALID;

ALTER TABLE purchaseOrder
    ADD CONSTRAINT fk_purchaseorder_placeofsupply_gststate
    FOREIGN KEY (placeOfSupply) REFERENCES gstState (code) NOT VALID;

ALTER TABLE salesOrder
    ADD CONSTRAINT fk_salesorder_shipto_gststate
    FOREIGN KEY (shipToStateCode) REFERENCES gstState (code) NOT VALID;

ALTER TABLE salesOrder
    ADD CONSTRAINT fk_salesorder_placeofsupply_gststate
    FOREIGN KEY (placeOfSupplyStateCode) REFERENCES gstState (code) NOT VALID;

ALTER TABLE taxInvoice
    ADD CONSTRAINT fk_taxinvoice_billto_gststate
    FOREIGN KEY (billToStateCode) REFERENCES gstState (code) NOT VALID;

ALTER TABLE taxInvoice
    ADD CONSTRAINT fk_taxinvoice_shipto_gststate
    FOREIGN KEY (shipToStateCode) REFERENCES gstState (code) NOT VALID;
