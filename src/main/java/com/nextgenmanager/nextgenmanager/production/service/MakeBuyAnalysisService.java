package com.nextgenmanager.nextgenmanager.production.service;

import com.nextgenmanager.nextgenmanager.production.dto.MakeBuyAnalysisDTO;
import com.nextgenmanager.nextgenmanager.production.dto.MakeBuyAnalysisRequestDTO;

import java.math.BigDecimal;

public interface MakeBuyAnalysisService {

    /**
     * Full Make-or-Buy analysis with explicit parameters.
     */
    MakeBuyAnalysisDTO analyze(MakeBuyAnalysisRequestDTO request);

    /**
     * Convenience overload: analyzes with default quantity = 1 and no price overrides.
     * Uses the active BOM for the item automatically.
     */
    MakeBuyAnalysisDTO analyzeQuick(int itemId, BigDecimal quantity);
}
