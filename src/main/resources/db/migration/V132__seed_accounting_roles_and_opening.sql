-- V132: Accounting roles (in public.role — shared) + openingbalance table (per-tenant).
-- MT note: role seeds go to public.role (shared); openingbalance table is per-tenant.
--          In MT fork: role inserts stay in public migrations; openingbalance → V132_1__.

-- Opening balance import table (per-tenant, no schema prefix)
CREATE TABLE IF NOT EXISTS openingbalance (
    id                  BIGSERIAL PRIMARY KEY,
    ledgerAccount_id    BIGINT NOT NULL REFERENCES ledgeraccount(id),
    contact_id          INTEGER REFERENCES contact(id),
    amount              NUMERIC(12,2) NOT NULL,
    drCr                VARCHAR(2) NOT NULL CHECK (drCr IN ('DR','CR')),
    openingDate         DATE NOT NULL,
    importedAt          TIMESTAMP NOT NULL DEFAULT NOW(),
    deletedDate         TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_openingbalance_account ON openingbalance(ledgerAccount_id);

-- FK from financialyear to voucher (circular — deferred to after voucher table exists)
ALTER TABLE financialyear
    ADD CONSTRAINT fk_fy_opening_voucher
    FOREIGN KEY (openingEntryVoucher_id)
    REFERENCES voucher(id);

-- ─── Accounting Roles (public schema — shared) ──────────────────────────────
INSERT INTO public.role (
    id, roleName, displayName, roleDescription, moduleName, roleType, isSystemRole, isActive,
    createdBy, updatedBy, creationDate, updatedDate, deletedDate
) VALUES
    (nextval('public.role_seq'), 'ROLE_ACCOUNTS_ADMIN', 'Accounts Admin',
        'Manage chart of accounts, financial years, approval policies', 'ACCOUNTING', 'MODULE', false, true,
        'SYSTEM', 'SYSTEM', now(), now(), null),
    (nextval('public.role_seq'), 'ROLE_ACCOUNTS_USER', 'Accounts User',
        'Create and view vouchers and reports', 'ACCOUNTING', 'MODULE', false, true,
        'SYSTEM', 'SYSTEM', now(), now(), null),
    (nextval('public.role_seq'), 'ROLE_ACCOUNTS_HEAD', 'Accounts Head',
        'Approve vouchers, reverse entries, lock/unlock periods, year-end close', 'ACCOUNTING', 'MODULE', false, true,
        'SYSTEM', 'SYSTEM', now(), now(), null)
ON CONFLICT (roleName) DO UPDATE
SET displayName    = EXCLUDED.displayName,
    roleDescription= EXCLUDED.roleDescription,
    moduleName     = EXCLUDED.moduleName,
    roleType       = EXCLUDED.roleType,
    isActive       = EXCLUDED.isActive,
    updatedBy      = EXCLUDED.updatedBy,
    updatedDate    = now(),
    deletedDate    = null;

-- Assign all three accounting roles to the admin user
INSERT INTO public.userrolemap (id, userId, roleId, createdBy, updatedBy, creationDate, updatedDate)
SELECT
    nextval('public.userrolemap_seq'),
    u.id,
    r.id,
    'SYSTEM', 'SYSTEM', now(), now()
FROM public.appuser u
JOIN public.role r ON r.roleName IN ('ROLE_ACCOUNTS_ADMIN','ROLE_ACCOUNTS_USER','ROLE_ACCOUNTS_HEAD')
WHERE u.username = 'admin'
ON CONFLICT (userId, roleId) DO NOTHING;
