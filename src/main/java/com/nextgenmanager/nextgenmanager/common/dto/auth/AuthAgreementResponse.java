package com.nextgenmanager.nextgenmanager.common.dto.auth;

import java.util.Date;

public record AuthAgreementResponse(
        String version,
        String title,
        String content,
        boolean accepted,
        Date acceptedDate
) {
}
