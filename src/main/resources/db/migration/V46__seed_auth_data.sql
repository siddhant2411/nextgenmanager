INSERT INTO public.role (
    id, roleName, displayName, roleDescription, moduleName, roleType, isSystemRole, isActive,
    createdBy, updatedBy, creationDate, updatedDate, deletedDate
) VALUES
    (nextval('public.role_seq'), 'ROLE_SUPER_ADMIN', 'Super Admin', 'Full system access', null, 'SYSTEM', true, true, 'SYSTEM', 'SYSTEM', now(), now(), null),
    (nextval('public.role_seq'), 'ROLE_ADMIN', 'Admin', 'System administrator', null, 'SYSTEM', true, true, 'SYSTEM', 'SYSTEM', now(), now(), null),
    (nextval('public.role_seq'), 'ROLE_USER', 'User', 'Basic authenticated user', null, 'SYSTEM', true, true, 'SYSTEM', 'SYSTEM', now(), now(), null),
    (nextval('public.role_seq'), 'ROLE_PRODUCTION_ADMIN', 'Production Admin', 'Production module administrator', 'PRODUCTION', 'MODULE', false, true, 'SYSTEM', 'SYSTEM', now(), now(), null),
    (nextval('public.role_seq'), 'ROLE_PRODUCTION_USER', 'Production User', 'Production module user', 'PRODUCTION', 'MODULE', false, true, 'SYSTEM', 'SYSTEM', now(), now(), null),
    (nextval('public.role_seq'), 'ROLE_INVENTORY_ADMIN', 'Inventory Admin', 'Inventory module administrator', 'INVENTORY', 'MODULE', false, true, 'SYSTEM', 'SYSTEM', now(), now(), null),
    (nextval('public.role_seq'), 'ROLE_INVENTORY_USER', 'Inventory User', 'Inventory module user', 'INVENTORY', 'MODULE', false, true, 'SYSTEM', 'SYSTEM', now(), now(), null),
    (nextval('public.role_seq'), 'ROLE_PURCHASE_ADMIN', 'Purchase Admin', 'Purchase module administrator', 'PURCHASE', 'MODULE', false, true, 'SYSTEM', 'SYSTEM', now(), now(), null),
    (nextval('public.role_seq'), 'ROLE_PURCHASE_USER', 'Purchase User', 'Purchase module user', 'PURCHASE', 'MODULE', false, true, 'SYSTEM', 'SYSTEM', now(), now(), null),
    (nextval('public.role_seq'), 'ROLE_SALES_ADMIN', 'Sales Admin', 'Sales module administrator', 'SALES', 'MODULE', false, true, 'SYSTEM', 'SYSTEM', now(), now(), null),
    (nextval('public.role_seq'), 'ROLE_SALES_USER', 'Sales User', 'Sales module user', 'SALES', 'MODULE', false, true, 'SYSTEM', 'SYSTEM', now(), now(), null)
ON CONFLICT (roleName) DO UPDATE
SET displayName = EXCLUDED.displayName,
    roleDescription = EXCLUDED.roleDescription,
    moduleName = EXCLUDED.moduleName,
    roleType = EXCLUDED.roleType,
    isSystemRole = EXCLUDED.isSystemRole,
    isActive = EXCLUDED.isActive,
    updatedBy = EXCLUDED.updatedBy,
    updatedDate = now(),
    deletedDate = null;

INSERT INTO public.appuser (
    id, username, passwordHash, email, isActive, isLocked, lastLoginDate,
    createdBy, updatedBy, creationDate, updatedDate, deletedDate
) VALUES (
    nextval('public.appuser_seq'),
    'admin',
    '$2a$10$6U4n.n2O6ts0wRHkm87V6O2gCChTl7HfIpYvc5JcdopePgE68EZL.',
    'admin@nextgen.local',
    true,
    false,
    null,
    'SYSTEM',
    'SYSTEM',
    now(),
    now(),
    null
)
ON CONFLICT (username) DO UPDATE
SET passwordHash = EXCLUDED.passwordHash,
    email = EXCLUDED.email,
    isActive = EXCLUDED.isActive,
    isLocked = EXCLUDED.isLocked,
    updatedBy = EXCLUDED.updatedBy,
    updatedDate = now(),
    deletedDate = null;

INSERT INTO public.userrolemap (
    id, userId, roleId, createdBy, updatedBy, creationDate, updatedDate
)
SELECT
    nextval('public.userrolemap_seq'),
    u.id,
    r.id,
    'SYSTEM',
    'SYSTEM',
    now(),
    now()
FROM public.appuser u
JOIN public.role r
    ON r.roleName IN (
        'ROLE_SUPER_ADMIN',
        'ROLE_ADMIN',
        'ROLE_USER',
        'ROLE_PRODUCTION_ADMIN',
        'ROLE_INVENTORY_ADMIN',
        'ROLE_PURCHASE_ADMIN',
        'ROLE_SALES_ADMIN'
    )
WHERE u.username = 'admin'
ON CONFLICT (userId, roleId) DO NOTHING;

