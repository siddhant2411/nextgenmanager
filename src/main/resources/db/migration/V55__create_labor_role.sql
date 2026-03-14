CREATE TABLE laborRole (
    id              BIGSERIAL PRIMARY KEY,
    roleCode        VARCHAR(50)  NOT NULL UNIQUE,
    roleName        VARCHAR(100) NOT NULL,
    costPerHour     NUMERIC(10,2),
    description     VARCHAR(500),
    active          BOOLEAN NOT NULL DEFAULT TRUE,
    creationDate    TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updatedDate     TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deletedDate     TIMESTAMP
);

CREATE INDEX idx_labor_role_code ON laborRole(roleCode);
