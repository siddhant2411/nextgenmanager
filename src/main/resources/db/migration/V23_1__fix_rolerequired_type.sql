ALTER TABLE ProductionJob
ALTER COLUMN rolerequired TYPE SMALLINT
USING rolerequired::smallint;