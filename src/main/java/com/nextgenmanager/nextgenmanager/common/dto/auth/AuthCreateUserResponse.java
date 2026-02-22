package com.nextgenmanager.nextgenmanager.common.dto.auth;

import java.util.List;

public record AuthCreateUserResponse(
        Long id,
        String username,
        String email,
        Boolean isActive,
        Boolean isLocked,
        List<String> roles
) {
}
