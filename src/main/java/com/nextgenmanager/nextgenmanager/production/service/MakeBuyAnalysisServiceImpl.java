package com.nextgenmanager.nextgenmanager.production.service;

import com.nextgenmanager.nextgenmanager.bom.dto.BomCostBreakdownDTO;
import com.nextgenmanager.nextgenmanager.bom.dto.BomDTO;
import com.nextgenmanager.nextgenmanager.bom.dto.OperationCostLineDTO;
import com.nextgenmanager.nextgenmanager.bom.service.BomService;
import com.nextgenmanager.nextgenmanager.items.model.InventoryItem;
import com.nextgenmanager.nextgenmanager.items.model.ItemVendorPrice;
import com.nextgenmanager.nextgenmanager.items.model.ProductFinanceSettings;
import com.nextgenmanager.nextgenmanager.items.model.PriceType;
import com.nextgenmanager.nextgenmanager.items.repository.InventoryItemRepository;
import com.nextgenmanager.nextgenmanager.items.service.ItemVendorPriceService;
import com.nextgenmanager.nextgenmanager.production.dto.MakeBuyAnalysisDTO;
import com.nextgenmanager.nextgenmanager.production.dto.MakeBuyAnalysisDTO.BuyAnalysis;
import com.nextgenmanager.nextgenmanager.production.dto.MakeBuyAnalysisDTO.MakeAnalysis;
import com.nextgenmanager.nextgenmanager.production.dto.MakeBuyAnalysisDTO.SubcontractAnalysis;
import com.nextgenmanager.nextgenmanager.production.dto.MakeBuyAnalysisRequestDTO;
import com.nextgenmanager.nextgenmanager.production.enums.CostType;
import com.nextgenmanager.nextgenmanager.production.enums.MakeBuyDecision;
import com.nextgenmanager.nextgenmanager.production.model.Routing;
import com.nextgenmanager.nextgenmanager.production.model.RoutingOperation;
import jakarta.persistence.EntityNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class MakeBuyAnalysisServiceImpl implements MakeBuyAnalysisService {

    private static final Logger logger = LoggerFactory.getLogger(MakeBuyAnalysisServiceImpl.class);

    /**
     * If makeUnitCost ≤ buyUnitCost × (1 + MAKE_PREFERENCE_MARGIN), we still recommend MAKE.
     * Accounts for intangibles: quality control, IP protection, workforce utilisation.
     */
    private static final double MAKE_PREFERENCE_MARGIN = 0.05;

    @Autowired
    private InventoryItemRepository inventoryItemRepository;

    @Autowired
    private BomService bomService;

    @Autowired
    private RoutingService routingService;

    @Autowired
    private ItemVendorPriceService itemVendorPriceService;

    @Override
    public MakeBuyAnalysisDTO analyze(MakeBuyAnalysisRequestDTO request) {

        InventoryItem item = inventoryItemRepository.findById(request.getItemId())
                .orElseThrow(() -> new EntityNotFoundException("Item not found: " + request.getItemId()));

        BigDecimal quantity = (request.getQuantity() != null && request.getQuantity().compareTo(BigDecimal.ZERO) > 0)
                ? request.getQuantity() : BigDecimal.ONE;

        MakeAnalysis makeAnalysis = buildMakeAnalysis(item, request.getBomId(), quantity);
        BuyAnalysis buyAnalysis = buildBuyAnalysis(item, request.getBuyPricePerUnitOverride(), quantity);
        SubcontractAnalysis subAnalysis = buildSubcontractAnalysis(
                item.getInventoryItemId(), makeAnalysis, request.getSubcontractRatePerUnitOverride(), quantity);

        return buildResult(item, quantity, makeAnalysis, buyAnalysis, subAnalysis);
    }

    @Override
    public MakeBuyAnalysisDTO analyzeQuick(int itemId, BigDecimal quantity) {
        MakeBuyAnalysisRequestDTO req = new MakeBuyAnalysisRequestDTO();
        req.setItemId(itemId);
        req.setQuantity(quantity);
        return analyze(req);
    }

    // ──────────────────────────────── make ────────────────────────────────

    private MakeAnalysis buildMakeAnalysis(InventoryItem item, Integer bomIdOverride, BigDecimal quantity) {

        // Resolve BOM
        BomDTO bomDto = null;
        try {
            bomDto = bomIdOverride != null
                    ? bomService.getBomDTO(bomIdOverride)
                    : bomService.getActiveBomByParentInventoryItem(item.getInventoryItemId());
        } catch (Exception e) {
            logger.debug("No active BOM found for item {}: {}", item.getItemCode(), e.getMessage());
        }

        if (bomDto == null) {
            return MakeAnalysis.builder().available(false).build();
        }

        int bomId = bomDto.getId();

        // Per-unit material cost from existing BomCostBreakdown (already per-unit)
        BomCostBreakdownDTO breakdown = bomService.getBomCostBreakdown(bomId);
        BigDecimal unitMaterialCost = breakdown.getTotalMaterialCost() != null
                ? breakdown.getTotalMaterialCost() : BigDecimal.ZERO;
        BigDecimal batchMaterialCost = unitMaterialCost.multiply(quantity).setScale(2, RoundingMode.HALF_UP);

        // Quantity-aware operation cost from routing
        BigDecimal unitSetupCost = BigDecimal.ZERO;
        BigDecimal unitRunCost = BigDecimal.ZERO;
        Long routingId = null;
        List<OperationCostLineDTO> opLines = new ArrayList<>();

        try {
            Routing routing = routingService.getRoutingEntityByBom(bomId);
            if (routing != null && routing.getOperations() != null) {
                routingId = routing.getId();
                for (RoutingOperation op : routing.getOperations()) {
                    BigDecimal[] costs = quantityAwareOpCost(op, quantity);
                    unitSetupCost = unitSetupCost.add(costs[0]);
                    unitRunCost = unitRunCost.add(costs[1]);
                    opLines.add(buildOpLine(op, costs));
                }
            }
        } catch (Exception e) {
            logger.debug("No routing found for BOM {}: {}", bomId, e.getMessage());
        }

        BigDecimal unitOperationCost = unitSetupCost.add(unitRunCost).setScale(4, RoundingMode.HALF_UP);
        BigDecimal batchOperationCost = unitOperationCost.multiply(quantity).setScale(2, RoundingMode.HALF_UP);
        BigDecimal unitTotalCost = unitMaterialCost.add(unitOperationCost).setScale(2, RoundingMode.HALF_UP);
        BigDecimal batchTotalCost = batchMaterialCost.add(batchOperationCost).setScale(2, RoundingMode.HALF_UP);

        return MakeAnalysis.builder()
                .available(true)
                .bomId(bomId)
                .routingId(routingId)
                .unitMaterialCost(unitMaterialCost)
                .batchMaterialCost(batchMaterialCost)
                .unitSetupCost(unitSetupCost.setScale(4, RoundingMode.HALF_UP))
                .unitRunCost(unitRunCost.setScale(4, RoundingMode.HALF_UP))
                .unitOperationCost(unitOperationCost)
                .batchOperationCost(batchOperationCost)
                .unitTotalCost(unitTotalCost)
                .batchTotalCost(batchTotalCost)
                .materialLines(breakdown.getMaterialCosts())
                .operationLines(opLines)
                .build();
    }

    /**
     * Quantity-aware operation cost per unit.
     *
     * For CALCULATED operations:
     *   setupCost  = rate × setupTime × (1 + overhead%)          ← one-time, amortized
     *   runCost    = rate × runTime × (1 + overhead%)             ← per unit (variable)
     *   rate       = machineCostPerHour + (laborCostPerHour × numOperators)
     *
     * For FIXED_RATE / SUB_CONTRACTED:
     *   entire fixedCostPerUnit treated as run cost (no setup component)
     *
     * @return [unitSetupCost (amortized over quantity), unitRunCost]
     */
    private BigDecimal[] quantityAwareOpCost(RoutingOperation op, BigDecimal quantity) {
        CostType costType = op.getCostType() != null ? op.getCostType() : CostType.CALCULATED;

        if (costType == CostType.FIXED_RATE || costType == CostType.SUB_CONTRACTED) {
            BigDecimal fixedCost = op.getFixedCostPerUnit() != null ? op.getFixedCostPerUnit() : BigDecimal.ZERO;
            return new BigDecimal[]{BigDecimal.ZERO, fixedCost};
        }

        BigDecimal setupTime = op.getSetupTime() != null ? op.getSetupTime() : BigDecimal.ZERO;
        BigDecimal runTime = op.getRunTime() != null ? op.getRunTime() : BigDecimal.ZERO;

        // Combined rate: machine + labor
        BigDecimal machineCostRate = BigDecimal.ZERO;
        if (op.getMachineDetails() != null && op.getMachineDetails().getCostPerHour() != null) {
            machineCostRate = op.getMachineDetails().getCostPerHour();
        } else if (op.getWorkCenter() != null && op.getWorkCenter().getMachineCostPerHour() != null) {
            machineCostRate = op.getWorkCenter().getMachineCostPerHour();
        }

        BigDecimal laborCostRate = BigDecimal.ZERO;
        int numOperators = op.getNumberOfOperators() != null ? op.getNumberOfOperators() : 1;
        if (op.getLaborRole() != null && op.getLaborRole().getCostPerHour() != null) {
            laborCostRate = op.getLaborRole().getCostPerHour();
        }

        BigDecimal combinedRate = machineCostRate.add(laborCostRate.multiply(BigDecimal.valueOf(numOperators)));

        BigDecimal overheadPct = (op.getWorkCenter() != null && op.getWorkCenter().getOverheadPercentage() != null)
                ? op.getWorkCenter().getOverheadPercentage() : BigDecimal.ZERO;
        BigDecimal overheadMultiplier = BigDecimal.ONE.add(
                overheadPct.divide(BigDecimal.valueOf(100), 6, RoundingMode.HALF_UP));

        // Setup: one-time → amortize over batch
        BigDecimal totalSetupCost = combinedRate.multiply(setupTime).multiply(overheadMultiplier);
        BigDecimal unitSetupCost = totalSetupCost.divide(quantity, 6, RoundingMode.HALF_UP);

        // Run: per unit
        BigDecimal unitRunCost = combinedRate.multiply(runTime).multiply(overheadMultiplier)
                .setScale(6, RoundingMode.HALF_UP);

        return new BigDecimal[]{unitSetupCost, unitRunCost};
    }

    private OperationCostLineDTO buildOpLine(RoutingOperation op, BigDecimal[] costs) {
        BigDecimal unitSetup = costs[0];
        BigDecimal unitRun = costs[1];
        BigDecimal total = unitSetup.add(unitRun).setScale(2, RoundingMode.HALF_UP);

        return OperationCostLineDTO.builder()
                .operationId(op.getId())
                .sequenceNumber(op.getSequenceNumber())
                .operationName(op.getName())
                .costType(op.getCostType() != null ? op.getCostType() : CostType.CALCULATED)
                .workCenterName(op.getWorkCenter() != null ? op.getWorkCenter().getCenterName() : null)
                .machineName(op.getMachineDetails() != null ? op.getMachineDetails().getMachineName() : null)
                .laborRoleName(op.getLaborRole() != null ? op.getLaborRole().getRoleName() : null)
                .numberOfOperators(op.getNumberOfOperators() != null ? op.getNumberOfOperators() : 1)
                .setupTime(op.getSetupTime())
                .runTime(op.getRunTime())
                .totalCost(total)
                .build();
    }

    // ──────────────────────────────── buy ────────────────────────────────

    private BuyAnalysis buildBuyAnalysis(InventoryItem item, BigDecimal override, BigDecimal quantity) {

        // 1. Manual override (one-off analysis)
        if (override != null && override.compareTo(BigDecimal.ZERO) > 0) {
            return BuyAnalysis.builder()
                    .available(true)
                    .unitCost(override)
                    .batchTotalCost(override.multiply(quantity).setScale(2, RoundingMode.HALF_UP))
                    .priceSource("MANUAL_OVERRIDE")
                    .leadTimeDays(getLeadTimeDays(item))
                    .build();
        }

        // 2. ItemVendorPrice — preferred vendor, then cheapest (primary source)
        java.util.Optional<ItemVendorPrice> vendorPrice =
                itemVendorPriceService.getBestPrice(item.getInventoryItemId(), PriceType.PURCHASE);
        if (vendorPrice.isPresent()) {
            ItemVendorPrice vp = vendorPrice.get();
            BigDecimal unitCost = vp.getPricePerUnit();
            Double leadTime = vp.getLeadTimeDays() != null ? vp.getLeadTimeDays().doubleValue() : getLeadTimeDays(item);
            return BuyAnalysis.builder()
                    .available(true)
                    .unitCost(unitCost)
                    .batchTotalCost(unitCost.multiply(quantity).setScale(2, RoundingMode.HALF_UP))
                    .priceSource("VENDOR_PRICE")
                    .vendorId(vp.getVendor().getId())
                    .vendorName(vp.getVendor().getCompanyName())
                    .gstRegistered(vp.isGstRegistered())
                    .leadTimeDays(leadTime)
                    .build();
        }

        // 3. Fallback: lastPurchaseCost on item master (legacy / manual)
        ProductFinanceSettings fin = item.getProductFinanceSettings();
        if (fin != null && fin.getLastPurchaseCost() != null && fin.getLastPurchaseCost() > 0) {
            BigDecimal unitCost = BigDecimal.valueOf(fin.getLastPurchaseCost());
            return BuyAnalysis.builder()
                    .available(true)
                    .unitCost(unitCost)
                    .batchTotalCost(unitCost.multiply(quantity).setScale(2, RoundingMode.HALF_UP))
                    .priceSource("LAST_PURCHASE")
                    .leadTimeDays(getLeadTimeDays(item))
                    .build();
        }
        if (fin != null && fin.getStandardCost() != null && fin.getStandardCost() > 0) {
            BigDecimal unitCost = BigDecimal.valueOf(fin.getStandardCost());
            return BuyAnalysis.builder()
                    .available(true)
                    .unitCost(unitCost)
                    .batchTotalCost(unitCost.multiply(quantity).setScale(2, RoundingMode.HALF_UP))
                    .priceSource("STANDARD_COST")
                    .leadTimeDays(getLeadTimeDays(item))
                    .build();
        }

        return BuyAnalysis.builder()
                .available(false)
                .leadTimeDays(getLeadTimeDays(item))
                .build();
    }

    private Double getLeadTimeDays(InventoryItem item) {
        if (item.getProductInventorySettings() != null) {
            double lt = item.getProductInventorySettings().getLeadTime();
            return lt > 0 ? lt : null;
        }
        return null;
    }

    // ──────────────────────────────── subcontract ────────────────────────────────

    /**
     * @param itemId      inventory item id — needed for ItemVendorPrice lookup
     * @param makeAnalysis already-computed MAKE analysis (provides material cost)
     * @param rateOverride optional one-off job-work rate override from the request
     * @param quantity     batch quantity
     */
    private SubcontractAnalysis buildSubcontractAnalysis(
            int itemId, MakeAnalysis makeAnalysis, BigDecimal rateOverride, BigDecimal quantity) {

        // Material cost comes from MAKE; without a BOM subcontract is not meaningful
        if (!makeAnalysis.isAvailable()) {
            return SubcontractAnalysis.builder().available(false).build();
        }

        BigDecimal unitJobWorkCost = null;
        Integer vendorId = null;
        String vendorName = null;
        Boolean gstRegistered = null;

        // 1. Manual override (one-off analysis)
        if (rateOverride != null && rateOverride.compareTo(BigDecimal.ZERO) > 0) {
            unitJobWorkCost = rateOverride;
        }

        // 2. ItemVendorPrice with priceType=JOB_WORK — preferred vendor, then cheapest
        if (unitJobWorkCost == null) {
            java.util.Optional<ItemVendorPrice> jobWorkPrice =
                    itemVendorPriceService.getBestPrice(itemId, PriceType.JOB_WORK);
            if (jobWorkPrice.isPresent()) {
                ItemVendorPrice vp = jobWorkPrice.get();
                unitJobWorkCost = vp.getPricePerUnit();
                vendorId = vp.getVendor().getId();
                vendorName = vp.getVendor().getCompanyName();
                gstRegistered = vp.isGstRegistered();
            }
        }

        // No job-work rate available
        if (unitJobWorkCost == null) {
            return SubcontractAnalysis.builder().available(false).build();
        }

        BigDecimal unitMaterialCost = makeAnalysis.getUnitMaterialCost();
        BigDecimal unitTotalCost = unitMaterialCost.add(unitJobWorkCost).setScale(2, RoundingMode.HALF_UP);

        return SubcontractAnalysis.builder()
                .available(true)
                .unitMaterialCost(unitMaterialCost)
                .unitJobWorkCost(unitJobWorkCost)
                .unitTotalCost(unitTotalCost)
                .batchTotalCost(unitTotalCost.multiply(quantity).setScale(2, RoundingMode.HALF_UP))
                .vendorId(vendorId)
                .vendorName(vendorName)
                .gstRegistered(gstRegistered)
                .build();
    }

    // ──────────────────────────────── recommendation ────────────────────────────────

    private MakeBuyAnalysisDTO buildResult(InventoryItem item, BigDecimal quantity,
            MakeAnalysis makeAnalysis, BuyAnalysis buyAnalysis, SubcontractAnalysis subAnalysis) {

        MakeBuyDecision decision;
        String reason;
        BigDecimal diffPct = null;
        BigDecimal breakEven = null;

        boolean canMake = makeAnalysis.isAvailable();
        boolean canBuy = buyAnalysis.isAvailable();
        boolean canSub = subAnalysis.isAvailable();

        if (!canMake && !canBuy) {
            decision = MakeBuyDecision.INSUFFICIENT_DATA;
            reason = "No active BOM and no purchase price available. Set up a BOM or update the last purchase cost to get a recommendation.";

        } else if (!canMake) {
            decision = MakeBuyDecision.BUY;
            reason = "No active BOM found for this item. Recommend purchasing from an external supplier.";

        } else if (!canBuy) {
            decision = MakeBuyDecision.MAKE;
            reason = "No purchase price data available for comparison. Defaulting to in-house manufacturing. Update 'Last Purchase Cost' to enable Make-or-Buy analysis.";

        } else {
            BigDecimal makeUnit = makeAnalysis.getUnitTotalCost();
            BigDecimal buyUnit = buyAnalysis.getUnitCost();

            diffPct = makeUnit.subtract(buyUnit)
                    .divide(buyUnit, 4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100))
                    .setScale(2, RoundingMode.HALF_UP);

            // Break-even: setup cost / (buyUnit - variableCostPerUnit)
            BigDecimal totalSetupCost = makeAnalysis.getUnitSetupCost().multiply(quantity);
            BigDecimal variableCostPerUnit = makeAnalysis.getUnitMaterialCost()
                    .add(makeAnalysis.getUnitRunCost());
            if (buyUnit.compareTo(variableCostPerUnit) > 0) {
                breakEven = totalSetupCost
                        .divide(buyUnit.subtract(variableCostPerUnit), 0, RoundingMode.CEILING);
            }

            // Check if subcontracting is the best option
            if (canSub) {
                BigDecimal subUnit = subAnalysis.getUnitTotalCost();
                if (subUnit.compareTo(makeUnit) < 0 && subUnit.compareTo(buyUnit) < 0) {
                    decision = MakeBuyDecision.SUBCONTRACT;
                    reason = String.format(
                            "Job work (subcontract) is the most economical option at ₹%.2f/unit " +
                            "(vs Make: ₹%.2f/unit, Buy: ₹%.2f/unit). " +
                            "Suitable for GST Section 143 job-work arrangement — you supply raw materials, vendor charges processing fee.",
                            subUnit, makeUnit, buyUnit);
                    return finalize(item, quantity, makeAnalysis, buyAnalysis, subAnalysis, decision, reason, diffPct, breakEven);
                }
            }

            BigDecimal threshold = buyUnit.multiply(BigDecimal.valueOf(1 + MAKE_PREFERENCE_MARGIN));
            if (makeUnit.compareTo(threshold) <= 0) {
                decision = MakeBuyDecision.MAKE;
                if (diffPct.compareTo(BigDecimal.ZERO) <= 0) {
                    reason = String.format(
                            "In-house manufacturing (₹%.2f/unit) is %.1f%% cheaper than buying (₹%.2f/unit). " +
                            "Recommend MAKE — retains production capability and quality control.",
                            makeUnit, diffPct.negate().doubleValue(), buyUnit);
                } else {
                    reason = String.format(
                            "Manufacturing cost (₹%.2f/unit) is within %.1f%% of purchase price (₹%.2f/unit). " +
                            "Recommend MAKE to maintain in-house expertise, avoid supplier dependence, and protect quality.",
                            makeUnit, diffPct.doubleValue(), buyUnit);
                }
            } else {
                decision = MakeBuyDecision.BUY;
                reason = String.format(
                        "Purchase cost (₹%.2f/unit) is significantly cheaper than manufacturing (₹%.2f/unit, %.1f%% higher). " +
                        "Recommend BUY — redirect production capacity to core value-add operations.%s",
                        buyUnit, makeUnit, diffPct.doubleValue(),
                        breakEven != null ? String.format(" Break-even at %s units.", breakEven.toPlainString()) : "");
            }
        }

        return finalize(item, quantity, makeAnalysis, buyAnalysis, subAnalysis, decision, reason, diffPct, breakEven);
    }

    private MakeBuyAnalysisDTO finalize(InventoryItem item, BigDecimal quantity,
            MakeAnalysis makeAnalysis, BuyAnalysis buyAnalysis, SubcontractAnalysis subAnalysis,
            MakeBuyDecision decision, String reason, BigDecimal diffPct, BigDecimal breakEven) {

        return MakeBuyAnalysisDTO.builder()
                .itemId(item.getInventoryItemId())
                .itemCode(item.getItemCode())
                .itemName(item.getName())
                .quantity(quantity)
                .makeAnalysis(makeAnalysis)
                .buyAnalysis(buyAnalysis)
                .subcontractAnalysis(subAnalysis)
                .recommendation(decision)
                .recommendationReason(reason)
                .makeBuyCostDifferencePct(diffPct)
                .breakEvenQuantity(breakEven)
                .build();
    }
}
