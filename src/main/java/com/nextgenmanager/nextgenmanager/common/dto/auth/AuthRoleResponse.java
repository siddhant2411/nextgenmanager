package com.nextgenmanager.nextgenmanager.common.dto.auth;

public record AuthRoleResponse(
        Long id,
        String roleName,
        String displayName,
        String roleDescription,
        String moduleName,
        String roleType,
        Boolean isSystemRole,
        Boolean isActive
) {
}
