package com.nextgenmanager.nextgenmanager.common.dto.auth;

public record AuthUpdateUserStatusRequest(
        Boolean isActive,
        Boolean isLocked
) {
}
