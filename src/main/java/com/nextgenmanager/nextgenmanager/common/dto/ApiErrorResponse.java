package com.nextgenmanager.nextgenmanager.common.dto;

import java.time.OffsetDateTime;

public record ApiErrorResponse(
        String timestamp,
        int status,
        String error,
        String message,
        String path
) {
    public static ApiErrorResponse of(int status, String error, String message, String path) {
        return new ApiErrorResponse(OffsetDateTime.now().toString(), status, error, message, path);
    }
}
