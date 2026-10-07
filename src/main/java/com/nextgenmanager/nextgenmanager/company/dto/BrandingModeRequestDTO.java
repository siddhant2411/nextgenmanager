package com.nextgenmanager.nextgenmanager.company.dto;

import com.nextgenmanager.nextgenmanager.company.model.BrandingMode;
import jakarta.validation.constraints.NotNull;

public record BrandingModeRequestDTO(@NotNull BrandingMode mode) {
}
