package com.nextgenmanager.nextgenmanager.common.dto.auth;

public record AuthCreateRoleRequest(
        String roleName,
        String displayName,
        String roleDescription,
        String moduleName,
        String roleType,
        Boolean isSystemRole,
        Boolean isActive
) {
}
