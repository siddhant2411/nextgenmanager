package com.nextgenmanager.nextgenmanager.Inventory.service;

import com.nextgenmanager.nextgenmanager.Inventory.model.*;
import com.nextgenmanager.nextgenmanager.Inventory.repository.InventoryProcurementOrderRepository;
import com.nextgenmanager.nextgenmanager.Inventory.repository.InventoryRequestRepository;
import com.nextgenmanager.nextgenmanager.bom.model.Bom;
import com.nextgenmanager.nextgenmanager.bom.model.routing.Routing;
import com.nextgenmanager.nextgenmanager.bom.repository.BomRepository;
import com.nextgenmanager.nextgenmanager.bom.repository.routing.RoutingRepository;
import com.nextgenmanager.nextgenmanager.items.model.InventoryItem;
import com.nextgenmanager.nextgenmanager.items.model.ProductInventorySettings;
import com.nextgenmanager.nextgenmanager.production.dto.WorkOrderDTO;
import com.nextgenmanager.nextgenmanager.production.dto.WorkOrderRequestDTO;
import com.nextgenmanager.nextgenmanager.production.enums.WorkOrderPriority;
import com.nextgenmanager.nextgenmanager.production.enums.WorkOrderSourceType;
import com.nextgenmanager.nextgenmanager.production.service.workorder.WorkOrderService;
import com.nextgenmanager.nextgenmanager.purchase.requisition.dto.PurchaseRequisitionCreateDto;
import com.nextgenmanager.nextgenmanager.purchase.requisition.dto.PurchaseRequisitionDto;
import com.nextgenmanager.nextgenmanager.purchase.requisition.dto.PurchaseRequisitionItemCreateDto;
import com.nextgenmanager.nextgenmanager.purchase.requisition.model.PurchaseRequisitionPriority;
import com.nextgenmanager.nextgenmanager.purchase.requisition.model.PurchaseRequisitionSource;
import com.nextgenmanager.nextgenmanager.purchase.requisition.service.PurchaseRequisitionService;
import com.nextgenmanager.nextgenmanager.sales.model.SalesOrder;
import com.nextgenmanager.nextgenmanager.sales.repository.SalesOrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;
import java.util.ArrayList;
import com.nextgenmanager.nextgenmanager.production.dto.WorkOrderLineRequestDTO;

/**
 * Routes order-linked (MAKE_TO_ORDER) procurement shortfalls to the right supply document,
 * per the item's route flags — "fully auto when unambiguous":
 *
 * <ul>
 *   <li>manufactured-only + active BOM with routing → draft Work Order (decision WORK_ORDER)</li>
 *   <li>purchased-only → draft Purchase Requisition (decision PURCHASE_ORDER)</li>
 *   <li>ambiguous (both flags), or manufactured without a usable BOM → left UNDECIDED for the
 *       Planning Desk, where a planner sees the make/buy recommendation and decides</li>
 * </ul>
 *
 * Best-effort: a failure to spawn a document leaves the need UNDECIDED rather than throwing.
 * Auto-created documents are drafts (WO = CREATED, PR = DRAFT) for planner review.
 */
@Service
@RequiredArgsConstructor
public class ProcurementRoutingService {

    private static final Logger log = LoggerFactory.getLogger(ProcurementRoutingService.class);

    private final InventoryRequestRepository inventoryRequestRepository;
    private final InventoryProcurementOrderRepository procurementOrderRepository;
    private final SalesOrderRepository salesOrderRepository;
    private final WorkOrderService workOrderService;
    private final PurchaseRequisitionService purchaseRequisitionService;
    private final BomRepository bomRepository;
    private final RoutingRepository routingRepository;

    /**
     * When true, a sales order's manufactured shortfalls become ONE work order with a line per
     * item instead of one work order each. Off by default: it changes long-standing auto-routing
     * behaviour, so it is opted into per deployment.
     */
    @Value("${nextgen.production.batch-work-orders-per-sales-order:false}")
    private boolean batchWorkOrdersPerSalesOrder;

    @Transactional
    public void routeForSalesOrder(Long salesOrderId) {
        SalesOrder so = salesOrderRepository.findById(salesOrderId).orElse(null);
        if (so == null) {
            log.warn("Procurement routing: SO {} not found — skipped", salesOrderId);
            return;
        }

        List<InventoryRequest> needs = inventoryRequestRepository
                .findBySourceIdAndRequestSource(salesOrderId, InventoryRequestSource.SALES_ORDER);

        List<InventoryProcurementOrder> pending = new ArrayList<>();
        for (InventoryRequest need : needs) {
            for (InventoryProcurementOrder order : procurementOrderRepository.findByInventoryRequestId(need.getId())) {
                if (order.getInventoryProcurementStatus() != InventoryProcurementStatus.CREATED) continue;
                if (order.getProcurementDecision() != ProcurementDecision.UNDECIDED) continue;
                pending.add(order);
            }
        }

        // With batching on, everything this SO needs to make becomes ONE work order with a line
        // per item instead of a separate work order each. Anything ambiguous or purchased still
        // falls through to the per-order routing below.
        if (batchWorkOrdersPerSalesOrder) {
            List<InventoryProcurementOrder> batched = routeManufacturedAsOneWorkOrder(pending, so);
            pending.removeAll(batched);
        }

        for (InventoryProcurementOrder order : pending) {
            try {
                routeOne(order, so);
            } catch (Exception e) {
                // Leave UNDECIDED for the Planning Desk; never break the chain.
                log.error("Procurement routing failed for order {} (item {}) — left UNDECIDED for the desk",
                        order.getId(),
                        order.getInventoryItem() != null ? order.getInventoryItem().getItemCode() : "?", e);
            }
        }
    }

    /**
     * Creates a single multi-line work order covering every unambiguously-manufactured need of
     * this sales order.
     *
     * <p>Only needs that {@link #routeOne} would itself have sent to a work order are taken:
     * manufactured-and-not-purchased, with an active BOM and a routing. Ambiguous items are left
     * for the Planning Desk exactly as before. If fewer than two qualify there is nothing to
     * batch and the caller's per-order path handles them, so behaviour for a single-item sales
     * order is unchanged.
     *
     * @return the orders that were folded into the batch work order
     */
    private List<InventoryProcurementOrder> routeManufacturedAsOneWorkOrder(
            List<InventoryProcurementOrder> pending, SalesOrder so) {

        List<InventoryProcurementOrder> eligible = new ArrayList<>();
        List<WorkOrderLineRequestDTO> lines = new ArrayList<>();

        for (InventoryProcurementOrder order : pending) {
            InventoryItem item = order.getInventoryItem();
            if (item == null) continue;

            ProductInventorySettings settings = item.getProductInventorySettings();
            boolean manufactured = settings != null && settings.isManufactured();
            boolean purchased = settings != null && settings.isPurchased();
            if (!manufactured || purchased) continue;

            BigDecimal shortfall = shortfallQty(order);
            if (shortfall.signum() <= 0) continue;

            Bom bom = bomRepository.findActiveBomWithPositionsByParentItemId(item.getInventoryItemId()).orElse(null);
            Routing routing = bom != null ? routingRepository.findByBomId(bom.getId()).orElse(null) : null;
            if (bom == null || routing == null) continue;

            WorkOrderLineRequestDTO line = new WorkOrderLineRequestDTO();
            line.setBomId(bom.getId());
            line.setRoutingId(routing.getId());
            line.setPlannedQuantity(shortfall);
            line.setDueDate(toDate(so.getDeliveryDate()));
            eligible.add(order);
            lines.add(line);
        }

        if (eligible.size() < 2) {
            return List.of();
        }

        try {
            WorkOrderRequestDTO dto = new WorkOrderRequestDTO();
            dto.setLines(lines);
            dto.setSalesOrderId(so.getId().intValue());
            dto.setSourceType(WorkOrderSourceType.SALES_ORDER);
            dto.setPriority(WorkOrderPriority.NORMAL);
            dto.setDueDate(toDate(so.getDeliveryDate()));
            dto.setRemarks("Auto-created from SO " + so.getOrderNumber()
                    + " (make-to-order, " + lines.size() + " items)");

            WorkOrderDTO wo = workOrderService.addWorkOrder(dto);

            for (InventoryProcurementOrder order : eligible) {
                order.setProcurementDecision(ProcurementDecision.WORK_ORDER);
                order.setOrderId((long) wo.getId());
                procurementOrderRepository.save(order);
            }
            log.info("Auto-routed {} manufactured items of SO {} into one Work Order {}",
                    lines.size(), so.getOrderNumber(), wo.getId());
            return eligible;

        } catch (Exception e) {
            // Fall back to one work order per need rather than stranding the whole sales order.
            log.error("Batched work order for SO {} failed — falling back to per-item routing",
                    so.getOrderNumber(), e);
            return List.of();
        }
    }

    // ──────────────────────── reorder (MTS) routing ───────────────────────────────────

    /**
     * Routes a stock-reorder procurement need (no linked Sales Order) the same way as
     * MTO: manufactured-only + BOM+routing → WO; purchased-only → PR; ambiguous → desk.
     */
    @Transactional
    public void routeForReorder(Long procurementOrderId) {
        InventoryProcurementOrder order = procurementOrderRepository.findById(procurementOrderId)
                .orElseThrow(() -> new IllegalArgumentException("Procurement order not found: " + procurementOrderId));

        InventoryItem item = order.getInventoryItem();
        if (item == null) return;

        ProductInventorySettings settings = item.getProductInventorySettings();
        boolean manufactured = settings != null && settings.isManufactured();
        boolean purchased    = settings != null && settings.isPurchased();
        BigDecimal qty = shortfallQty(order);
        if (qty.signum() <= 0) return;

        try {
            if (manufactured && !purchased) {
                Bom bom = bomRepository.findActiveBomWithPositionsByParentItemId(item.getInventoryItemId()).orElse(null);
                Routing routing = bom != null ? routingRepository.findByBomId(bom.getId()).orElse(null) : null;
                if (bom != null && routing != null) {
                    int woId = createWorkOrderForReorder(item, bom, routing, qty);
                    order.setProcurementDecision(ProcurementDecision.WORK_ORDER);
                    order.setOrderId((long) woId);
                    procurementOrderRepository.save(order);
                    log.info("Reorder: auto-routed item {} qty {} to Work Order {}", item.getItemCode(), qty, woId);
                } else {
                    log.info("Reorder: item {} manufactured but no active BOM+routing — left UNDECIDED for the desk", item.getItemCode());
                }
            } else if (purchased && !manufactured) {
                Long prId = createPurchaseRequisitionForReorder(item, qty, order.getCreatedBy());
                order.setProcurementDecision(ProcurementDecision.PURCHASE_ORDER);
                order.setOrderId(prId);
                procurementOrderRepository.save(order);
                log.info("Reorder: auto-routed item {} qty {} to Purchase Requisition {}", item.getItemCode(), qty, prId);
            } else {
                log.info("Reorder: item {} make/buy ambiguous (manufactured={}, purchased={}) — left UNDECIDED for the desk",
                        item.getItemCode(), manufactured, purchased);
            }
        } catch (Exception e) {
            log.error("Reorder routing failed for item {} — left UNDECIDED for the desk", item.getItemCode(), e);
        }
    }

    private int createWorkOrderForReorder(InventoryItem item, Bom bom, Routing routing, BigDecimal qty) {
        WorkOrderRequestDTO dto = new WorkOrderRequestDTO();
        dto.setBomId(bom.getId());
        dto.setRoutingId(routing.getId());
        dto.setPlannedQuantity(qty);
        dto.setSourceType(WorkOrderSourceType.MANUAL);
        dto.setPriority(WorkOrderPriority.NORMAL);
        dto.setRemarks("Auto-created from stock reorder trigger for item " + item.getItemCode());
        WorkOrderDTO wo = workOrderService.addWorkOrder(dto);
        return wo.getId();
    }

    private Long createPurchaseRequisitionForReorder(InventoryItem item, BigDecimal qty, String requestedBy) {
        PurchaseRequisitionItemCreateDto line = new PurchaseRequisitionItemCreateDto(
                (long) item.getInventoryItemId(),
                1,
                item.getName(),
                qty.doubleValue(),
                null, null, null, null,
                "Stock reorder — replenishment to max stock level");

        PurchaseRequisitionCreateDto dto = new PurchaseRequisitionCreateDto(
                new Date(), null,
                requestedBy != null ? requestedBy : "SYSTEM",
                null, null,
                PurchaseRequisitionPriority.NORMAL,
                PurchaseRequisitionSource.REORDER,
                null, null,
                List.of(line),
                "Auto-created from stock reorder trigger for item " + item.getItemCode());

        PurchaseRequisitionDto pr = purchaseRequisitionService.create(dto);
        return pr.id();
    }

    // ──────────────────────── manual decisions (Planning Desk) ────────────────────────

    /** Planner explicitly routes an UNDECIDED need to a Work Order. */
    @Transactional
    public Long decideWorkOrder(Long procurementOrderId) {
        InventoryProcurementOrder order = loadDecidable(procurementOrderId);
        SalesOrder so = resolveSalesOrder(order); // null for stock-maintained (reorder/manual) needs
        InventoryItem item = order.getInventoryItem();
        Bom bom = bomRepository.findActiveBomWithPositionsByParentItemId(item.getInventoryItemId())
                .orElseThrow(() -> new IllegalStateException(
                        "Item " + item.getItemCode() + " has no active BOM — cannot create a Work Order."));
        Routing routing = routingRepository.findByBomId(bom.getId())
                .orElseThrow(() -> new IllegalStateException(
                        "BOM of item " + item.getItemCode() + " has no routing — cannot create a Work Order."));
        BigDecimal qty = shortfallQty(order);
        int woId = so != null ? createWorkOrder(so, bom, routing, qty)
                              : createWorkOrderForReorder(item, bom, routing, qty);
        order.setProcurementDecision(ProcurementDecision.WORK_ORDER);
        order.setOrderId((long) woId);
        procurementOrderRepository.save(order);
        return (long) woId;
    }

    /** Planner explicitly routes an UNDECIDED need to a Purchase Requisition. */
    @Transactional
    public Long decidePurchase(Long procurementOrderId) {
        InventoryProcurementOrder order = loadDecidable(procurementOrderId);
        SalesOrder so = resolveSalesOrder(order); // null for stock-maintained (reorder/manual) needs
        InventoryItem item = order.getInventoryItem();
        BigDecimal qty = shortfallQty(order);
        Long prId = so != null ? createPurchaseRequisition(so, item, qty, order.getCreatedBy())
                               : createPurchaseRequisitionForReorder(item, qty, order.getCreatedBy());
        order.setProcurementDecision(ProcurementDecision.PURCHASE_ORDER);
        order.setOrderId(prId);
        procurementOrderRepository.save(order);
        return prId;
    }

    /** Planner declines this auto-raised need (e.g. will be covered by stock / reorder). */
    @Transactional
    public void defer(Long procurementOrderId) {
        InventoryProcurementOrder order = loadDecidable(procurementOrderId);
        order.setInventoryProcurementStatus(InventoryProcurementStatus.CANCELED);
        procurementOrderRepository.save(order);
    }

    private InventoryProcurementOrder loadDecidable(Long procurementOrderId) {
        InventoryProcurementOrder order = procurementOrderRepository.findById(procurementOrderId)
                .orElseThrow(() -> new IllegalArgumentException("Procurement order not found: " + procurementOrderId));
        if (order.getInventoryProcurementStatus() != InventoryProcurementStatus.CREATED
                || order.getProcurementDecision() != ProcurementDecision.UNDECIDED) {
            throw new IllegalStateException("Procurement order " + procurementOrderId + " is no longer awaiting a decision.");
        }
        return order;
    }

    /**
     * Resolves the Sales Order behind a make-to-order need, or {@code null} when the need is
     * stock-maintained (reorder/manual — raw materials and finished goods held to stock, not tied
     * to any SO). Only a {@code SALES_ORDER}-sourced request carries a real SO id; a present-but-
     * missing SO id is still an error.
     */
    private SalesOrder resolveSalesOrder(InventoryProcurementOrder order) {
        InventoryRequest req = order.getInventoryRequest();
        Long soId = (req != null && req.getRequestSource() == InventoryRequestSource.SALES_ORDER)
                ? req.getSourceId() : null;
        if (soId == null) return null; // stock-maintained need — not tied to a sales order
        return salesOrderRepository.findById(soId)
                .orElseThrow(() -> new IllegalStateException("Linked sales order " + soId + " not found."));
    }

    private void routeOne(InventoryProcurementOrder order, SalesOrder so) {
        InventoryItem item = order.getInventoryItem();
        if (item == null) return;

        ProductInventorySettings settings = item.getProductInventorySettings();
        boolean manufactured = settings != null && settings.isManufactured();
        boolean purchased = settings != null && settings.isPurchased();

        BigDecimal shortfall = shortfallQty(order);
        if (shortfall.signum() <= 0) return;

        if (manufactured && !purchased) {
            Bom bom = bomRepository.findActiveBomWithPositionsByParentItemId(item.getInventoryItemId()).orElse(null);
            Routing routing = bom != null ? routingRepository.findByBomId(bom.getId()).orElse(null) : null;
            if (bom == null || routing == null) {
                log.info("Item {} is manufactured but has no active BOM+routing — left UNDECIDED for the desk",
                        item.getItemCode());
                return; // desk decides
            }
            int woId = createWorkOrder(so, bom, routing, shortfall);
            order.setProcurementDecision(ProcurementDecision.WORK_ORDER);
            order.setOrderId((long) woId);
            procurementOrderRepository.save(order);
            log.info("Auto-routed item {} qty {} to Work Order {} (SO {})",
                    item.getItemCode(), shortfall, woId, so.getOrderNumber());

        } else if (purchased && !manufactured) {
            Long prId = createPurchaseRequisition(so, item, shortfall, order.getCreatedBy());
            order.setProcurementDecision(ProcurementDecision.PURCHASE_ORDER);
            order.setOrderId(prId);
            procurementOrderRepository.save(order);
            log.info("Auto-routed item {} qty {} to Purchase Requisition {} (SO {})",
                    item.getItemCode(), shortfall, prId, so.getOrderNumber());

        } else {
            // Ambiguous (both flags) or neither — planner decides on the desk.
            log.info("Item {} make/buy is ambiguous (manufactured={}, purchased={}) — left UNDECIDED for the desk",
                    item.getItemCode(), manufactured, purchased);
        }
    }

    private int createWorkOrder(SalesOrder so, Bom bom, Routing routing, BigDecimal qty) {
        WorkOrderRequestDTO dto = new WorkOrderRequestDTO();
        dto.setBomId(bom.getId());
        dto.setRoutingId(routing.getId());
        dto.setPlannedQuantity(qty);
        dto.setSalesOrderId(so.getId().intValue());
        dto.setSourceType(WorkOrderSourceType.SALES_ORDER);
        dto.setPriority(WorkOrderPriority.NORMAL);
        dto.setDueDate(toDate(so.getDeliveryDate()));
        dto.setRemarks("Auto-created from SO " + so.getOrderNumber() + " (make-to-order)");
        WorkOrderDTO wo = workOrderService.addWorkOrder(dto);
        return wo.getId();
    }

    private Long createPurchaseRequisition(SalesOrder so, InventoryItem item, BigDecimal qty, String requestedBy) {
        PurchaseRequisitionItemCreateDto line = new PurchaseRequisitionItemCreateDto(
                (long) item.getInventoryItemId(),
                1,
                item.getName(),
                qty.doubleValue(),
                toDate(so.getDeliveryDate()),
                null,
                null,
                null,
                "Auto-created from SO " + so.getOrderNumber() + " (make-to-order)");

        PurchaseRequisitionCreateDto dto = new PurchaseRequisitionCreateDto(
                new Date(),
                toDate(so.getDeliveryDate()),
                requestedBy,
                null,
                null,
                PurchaseRequisitionPriority.NORMAL,
                PurchaseRequisitionSource.SALES_ORDER,
                so.getId(),
                so.getOrderNumber(),
                List.of(line),
                "Auto-created from SO " + so.getOrderNumber() + " (make-to-order)");

        PurchaseRequisitionDto pr = purchaseRequisitionService.create(dto);
        return pr.id();
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

    private static Date toDate(LocalDate d) {
        return d != null ? Date.from(d.atStartOfDay(ZoneId.systemDefault()).toInstant()) : null;
    }
}
