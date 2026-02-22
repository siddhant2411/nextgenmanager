package com.nextgenmanager.nextgenmanager.common.dto.auth;

public record AuthChangePasswordRequest(
        String currentPassword,
        String newPassword
) {
}
