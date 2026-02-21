package com.nextgenmanager.nextgenmanager.common.dto.auth;

public record AuthLoginRequest(
        String username,
        String password
) {
}

