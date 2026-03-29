ALTER TABLE bom
ADD COLUMN IF NOT EXISTS versionNumber INT ,
ADD COLUMN IF NOT EXISTS isActiveVersion BOOLEAN ,
ADD COLUMN IF NOT EXISTS versionGroup VARCHAR(20);


ALTER TABLE bom
DROP CONSTRAINT IF EXISTS uk_bom_parentinventoryitemid;

ALTER TABLE bom
ADD CONSTRAINT uq_bom_one_active_per_item
UNIQUE (parentinventoryitemid, isActiveVersion);


UPDATE bom
SET versionNumber = COALESCE(versionNumber, 1),
    isActiveVersion = COALESCE(isActiveVersion, FALSE)
WHERE versionNumber IS NULL OR isActiveVersion IS NULL;

UPDATE bom
SET versionGroup = SUBSTRING(md5(random()::text || now()::text), 1, 10)
WHERE versionGroup IS NULL OR versionGroup = '' OR versionGroup = 'default';


UPDATE bom
SET isActiveVersion = TRUE
WHERE ISActive = TRUE
  AND (isActiveVersion IS NULL OR isActiveVersion = FALSE);

ALTER TABLE bom ALTER COLUMN versionNumber SET NOT NULL;
ALTER TABLE bom ALTER COLUMN isActiveVersion SET NOT NULL;
ALTER TABLE bom ALTER COLUMN versionGroup SET NOT NULL;




CREATE INDEX idx_bom_status ON bom(bomStatus);
CREATE INDEX idx_bom_active_version ON bom(isActiveVersion);
CREATE INDEX idx_bom_version_group ON bom(versionGroup);
CREATE INDEX idx_bom_parent_item ON bom(parentinventoryitemid);
CREATE INDEX idx_bom_active_item ON bom(parentinventoryitemid, isActiveVersion);






