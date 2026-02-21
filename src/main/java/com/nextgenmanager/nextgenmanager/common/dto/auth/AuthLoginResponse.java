package com.nextgenmanager.nextgenmanager.common.dto.auth;

import java.util.List;

public record AuthLoginResponse(
        String accessToken,
        String refreshToken,
        String tokenType,
        long accessTokenExpiresIn,
        long refreshTokenExpiresIn,
        String username,
        List<String> roles
) {
}
