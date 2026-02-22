package com.nextgenmanager.nextgenmanager.common.dto.auth;

public record AuthUpdateRoleRequest(
        String displayName,
        String roleDescription,
        String moduleName,
        Boolean isActive
) {
}
