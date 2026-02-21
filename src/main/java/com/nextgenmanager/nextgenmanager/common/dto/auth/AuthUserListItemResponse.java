package com.nextgenmanager.nextgenmanager.common.dto.auth;

import java.util.Date;
import java.util.List;

public record AuthUserListItemResponse(
        Long id,
        String username,
        String email,
        Boolean isActive,
        Boolean isLocked,
        Date lastLoginDate,
        Date creationDate,
        List<String> roles
) {
}
