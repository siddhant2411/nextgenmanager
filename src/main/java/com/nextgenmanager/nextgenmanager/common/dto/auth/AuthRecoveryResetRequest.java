package com.nextgenmanager.nextgenmanager.common.dto.auth;

public record AuthRecoveryResetRequest(
        String recoverySecret,
        String username,
        String newPassword
) {
}
