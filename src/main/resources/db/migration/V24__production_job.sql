
ALTER TABLE ProductionJob
    ALTER COLUMN rolerequired TYPE SMALLINT;

ALTER TABLE productionjob DROP CONSTRAINT productionjob_rolerequired_check;
