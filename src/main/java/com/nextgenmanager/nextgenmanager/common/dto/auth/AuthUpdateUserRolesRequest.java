package com.nextgenmanager.nextgenmanager.common.dto.auth;

import java.util.List;

public record AuthUpdateUserRolesRequest(
        List<String> roleNames
) {
}
