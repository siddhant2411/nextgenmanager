package com.nextgenmanager.nextgenmanager.Inventory.service;

import com.nextgenmanager.nextgenmanager.Inventory.dto.PlanningDeskRowDto;
import com.nextgenmanager.nextgenmanager.Inventory.model.InventoryInstance;
import com.nextgenmanager.nextgenmanager.Inventory.model.InventoryProcurementOrder;
import com.nextgenmanager.nextgenmanager.Inventory.model.InventoryProcurementStatus;
import com.nextgenmanager.nextgenmanager.Inventory.model.ProcurementDecision;
import com.nextgenmanager.nextgenmanager.Inventory.repository.InventoryProcurementOrderRepository;
import com.nextgenmanager.nextgenmanager.bom.service.routing.MakeBuyAnalysisService;
import com.nextgenmanager.nextgenmanager.items.model.InventoryItem;
import com.nextgenmanager.nextgenmanager.items.model.ProductInventorySettings;
import com.nextgenmanager.nextgenmanager.production.dto.MakeBuyAnalysisDTO;
import com.nextgenmanager.nextgenmanager.sales.model.SalesOrder;
import com.nextgenmanager.nextgenmanager.sales.repository.SalesOrderRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Read model + actions for the Procurement Planning Desk. Lists procurement needs that
 * could not be auto-routed (ambiguous make/buy, or manufactured without a usable BOM) and
 * shows the make-or-buy recommendation for each. Decisions delegate to {@link ProcurementRoutingService}.
 */
@Service
@RequiredArgsConstructor
public class ProcurementPlanningService {

    private static final Logger log = LoggerFactory.getLogger(ProcurementPlanningService.class);

    private final InventoryProcurementOrderRepository procurementOrderRepository;
    private final SalesOrderRepository salesOrderRepository;
    private final MakeBuyAnalysisService makeBuyAnalysisService;
    private final ProcurementRoutingService procurementRoutingService;

    @Transactional(readOnly = true)
    public List<PlanningDeskRowDto> getQueue() {
        List<InventoryProcurementOrder> orders = procurementOrderRepository
                .findByProcurementDecisionAndInventoryProcurementStatus(
                        ProcurementDecision.UNDECIDED, InventoryProcurementStatus.CREATED);

        List<PlanningDeskRowDto> rows = new ArrayList<>();
        for (InventoryProcurementOrder order : orders) {
            InventoryItem item = order.getInventoryItem();
            if (item == null) continue;

            ProductInventorySettings settings = item.getProductInventorySettings();
            boolean manufactured = settings != null && settings.isManufactured();
            boolean purchased = settings != null && settings.isPurchased();
            BigDecimal shortfall = shortfallQty(order);

            String undecidedReason = (manufactured && purchased)
                    ? "Item can be both made and bought — needs a decision."
                    : manufactured
                        ? "Manufactured item without a usable BOM/routing — needs a decision."
                        : "No route configured (neither manufactured nor purchased).";

            // Reuse the existing cost-based Make/Buy engine for the recommendation.
            MakeBuyAnalysisDTO analysis = null;
            try {
                analysis = makeBuyAnalysisService.analyzeQuick(item.getInventoryItemId(), shortfall);
            } catch (Exception e) {
                log.warn("Make/Buy analysis failed for item {} on desk: {}", item.getItemCode(), e.getMessage());
            }

            SalesOrder so = resolveSalesOrder(order);

            rows.add(new PlanningDeskRowDto(
                    order.getId(),
                    so != null ? so.getId() : null,
                    so != null ? so.getOrderNumber() : null,
                    item.getInventoryItemId(),
                    item.getItemCode(),
                    item.getName(),
                    shortfall,
                    manufactured,
                    purchased,
                    undecidedReason,
                    analysis != null ? analysis.getRecommendation() : null,
                    analysis != null ? analysis.getRecommendationReason() : null,
                    analysis != null && analysis.getMakeAnalysis() != null ? analysis.getMakeAnalysis().getUnitTotalCost() : null,
                    analysis != null && analysis.getBuyAnalysis() != null ? analysis.getBuyAnalysis().getUnitCost() : null,
                    order.getCreationDate()));
        }
        return rows;
    }

    public Long decideMake(Long procurementOrderId) {
        return procurementRoutingService.decideWorkOrder(procurementOrderId);
    }

    public Long decideBuy(Long procurementOrderId) {
        return procurementRoutingService.decidePurchase(procurementOrderId);
    }

    public void defer(Long procurementOrderId) {
        procurementRoutingService.defer(procurementOrderId);
    }

    private SalesOrder resolveSalesOrder(InventoryProcurementOrder order) {
        Long soId = order.getInventoryRequest() != null ? order.getInventoryRequest().getSourceId() : null;
        return soId != null ? salesOrderRepository.findById(soId).orElse(null) : null;
    }

    private BigDecimal shortfallQty(InventoryProcurementOrder order) {
        List<InventoryInstance> pending = order.getPendingInventoryList();
        if (pending == null || pending.isEmpty()) return BigDecimal.ZERO;
        BigDecimal sum = pending.stream()
                .map(InventoryInstance::getQuantity)
                .filter(q -> q != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        // NOS pending instances may carry no per-unit quantity — fall back to instance count.
        return sum.signum() > 0 ? sum : BigDecimal.valueOf(pending.size());
    }
}
