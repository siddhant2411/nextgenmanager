-- V133: accountgroup is mapped with @CreationTimestamp/@UpdateTimestamp (creationDate,
-- updatedDate) but V128 created the table without those columns, causing
-- "column ag1_0.creationdate does not exist" on any AccountGroup query.
-- MT note: per-tenant (no schema prefix). Mirror as V133_1__ in MT fork.

ALTER TABLE accountgroup
    ADD COLUMN IF NOT EXISTS creationDate TIMESTAMP NOT NULL DEFAULT NOW(),
    ADD COLUMN IF NOT EXISTS updatedDate  TIMESTAMP NOT NULL DEFAULT NOW();
