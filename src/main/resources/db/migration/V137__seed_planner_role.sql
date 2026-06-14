-- V137: ROLE_PLANNER for the Procurement Planning Desk (make/buy decisions).
-- Owned by planning/procurement, distinct from store/warehouse. Seeded in public.role (shared).
-- MT note: role seeds stay in public migrations.

INSERT INTO public.role (
    id, roleName, displayName, roleDescription, moduleName, roleType, isSystemRole, isActive,
    createdBy, updatedBy, creationDate, updatedDate, deletedDate
) VALUES
    (nextval('public.role_seq'), 'ROLE_PLANNER', 'Procurement Planner',
        'Make-or-buy decisions on procurement needs: route to Work Order or Purchase Requisition', 'PLANNING', 'MODULE', false, true,
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

-- Assign the planner role to the admin user
INSERT INTO public.userrolemap (id, userId, roleId, createdBy, updatedBy, creationDate, updatedDate)
SELECT
    nextval('public.userrolemap_seq'),
    u.id,
    r.id,
    'SYSTEM', 'SYSTEM', now(), now()
FROM public.appuser u
JOIN public.role r ON r.roleName = 'ROLE_PLANNER'
WHERE u.username = 'admin'
ON CONFLICT (userId, roleId) DO NOTHING;
