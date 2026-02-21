package com.nextgenmanager.nextgenmanager.common.dto.auth;

import java.util.List;

public record AuthCreateUserRequest(
        String username,
        String password,
        String email,
        List<String> roleNames
) {
}
