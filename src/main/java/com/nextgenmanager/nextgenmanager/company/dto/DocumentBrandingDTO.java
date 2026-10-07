package com.nextgenmanager.nextgenmanager.company.dto;

import com.nextgenmanager.nextgenmanager.company.model.BrandingMode;

import java.util.Date;

public record DocumentBrandingDTO(
        BrandingMode mode,
        boolean hasLogo,
        Integer logoWidthPx,
        Integer logoHeightPx,
        boolean hasLetterhead,
        Integer letterheadWidthPx,
        Integer letterheadHeightPx,
        Date updatedDate
) {
}
