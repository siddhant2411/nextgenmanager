CREATE SEQUENCE productspecification_SEQ START 1 INCREMENT 50;
ALTER TABLE productspecification ALTER COLUMN id SET DEFAULT nextval('productspecification_seq');
