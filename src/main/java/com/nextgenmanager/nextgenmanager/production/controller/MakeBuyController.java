package com.nextgenmanager.nextgenmanager.production.controller;

import com.nextgenmanager.nextgenmanager.production.dto.MakeBuyAnalysisDTO;
import com.nextgenmanager.nextgenmanager.production.dto.MakeBuyAnalysisRequestDTO;
import com.nextgenmanager.nextgenmanager.production.service.MakeBuyAnalysisService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;

@RestController
@RequestMapping("/api/make-or-buy")
@Tag(name = "Make or Buy", description = "Quantitative Make-vs-Buy-vs-Subcontract analysis for Indian MSME manufacturing decisions")
public class MakeBuyController {

    @Autowired
    private MakeBuyAnalysisService makeBuyAnalysisService;

    /**
     * Full analysis with explicit BOM, quantity, and price overrides.
     *
     * POST /api/make-or-buy/analyze
     */
    @PostMapping("/analyze")
    @PreAuthorize("hasAnyRole('ROLE_ADMIN', 'ROLE_SUPER_ADMIN')")
    @Operation(
            summary = "Run Make-or-Buy analysis",
            description = "Compares in-house manufacturing cost (MAKE), purchase from supplier (BUY), " +
                          "and job-work subcontracting (SUBCONTRACT) for the given item and batch quantity. " +
                          "Returns a quantitative breakdown and a recommendation with rationale."
    )
    public ResponseEntity<MakeBuyAnalysisDTO> analyze(@RequestBody MakeBuyAnalysisRequestDTO request) {
        MakeBuyAnalysisDTO result = makeBuyAnalysisService.analyze(request);
        return ResponseEntity.ok(result);
    }

    /**
     * Quick analysis using the item's active BOM and default purchase price.
     * No request body needed — just the item ID and quantity as path/query params.
     *
     * GET /api/make-or-buy/analyze/{itemId}?quantity=100
     */
    @GetMapping("/analyze/{itemId}")
    @PreAuthorize("hasAnyRole('ROLE_ADMIN', 'ROLE_SUPER_ADMIN')")
    @Operation(
            summary = "Quick Make-or-Buy analysis",
            description = "Convenience endpoint: uses the active BOM and last purchase cost automatically. " +
                          "Quantity defaults to 1 if not provided."
    )
    public ResponseEntity<MakeBuyAnalysisDTO> analyzeQuick(
            @PathVariable int itemId,
            @Parameter(description = "Batch quantity to analyse. Defaults to 1.")
            @RequestParam(required = false, defaultValue = "1") BigDecimal quantity) {

        MakeBuyAnalysisDTO result = makeBuyAnalysisService.analyzeQuick(itemId, quantity);
        return ResponseEntity.ok(result);
    }
}
