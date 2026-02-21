package com.nextgenmanager.nextgenmanager.common.dto.auth;

import java.util.List;

public record AuthUserResponse(
        String username,
        List<String> roles
) {
}

