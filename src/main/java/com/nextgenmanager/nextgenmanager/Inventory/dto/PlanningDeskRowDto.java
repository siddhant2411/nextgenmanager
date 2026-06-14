package com.nextgenmanager.nextgenmanager.Inventory.dto;

import com.nextgenmanager.nextgenmanager.production.enums.MakeBuyDecision;

import java.math.BigDecimal;
import java.util.Date;

/**
 * One row on the Procurement Planning Desk: a need awaiting a make/buy decision,
 * enriched with the system's make-or-buy recommendation so the planner can act at a glance.
 */
public record PlanningDeskRowDto(
        Long procurementOrderId,
        Long salesOrderId,
        String salesOrderNumber,
        Integer itemId,
        String itemCode,
        String itemName,
        BigDecimal shortfallQty,
        boolean manufactured,
        boolean purchased,
        // Why it needs a human: ambiguous (both flags) or manufactured without a usable BOM.
        String undecidedReason,
        // System recommendation (reused from the existing Make/Buy cost analysis engine).
        MakeBuyDecision recommendedDecision,
        String recommendationReason,
        BigDecimal makeUnitCost,
        BigDecimal buyUnitCost,
        Date raisedOn
) {}
