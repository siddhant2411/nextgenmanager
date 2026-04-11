package com.nextgenmanager.nextgenmanager.common.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@Builder
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ErrorResponse {
    private int statusCode;
    private String errorCode;        // e.g., "RESOURCE_NOT_FOUND", "INVALID_INPUT"
    private String message;           // User-friendly error message
    private List<String> details;     // Detailed validation errors
    private LocalDateTime timestamp;
    private String path;              // Request path
    
    public ErrorResponse(int statusCode, String errorCode, String message) {
        this.statusCode = statusCode;
        this.errorCode = errorCode;
        this.message = message;
        this.timestamp = LocalDateTime.now();
    }
}
