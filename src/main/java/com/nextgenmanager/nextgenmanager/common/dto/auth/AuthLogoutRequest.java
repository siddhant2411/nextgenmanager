package com.nextgenmanager.nextgenmanager.common.dto.auth;

public record AuthLogoutRequest(
        String refreshToken
) {
}
