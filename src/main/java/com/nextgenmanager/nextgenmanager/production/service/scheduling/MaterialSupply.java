package com.nextgenmanager.nextgenmanager.production.service.scheduling;

import com.nextgenmanager.nextgenmanager.Inventory.model.InventoryApprovalStatus;
import com.nextgenmanager.nextgenmanager.Inventory.model.InventoryRequest;
import com.nextgenmanager.nextgenmanager.Inventory.repository.InventoryRequestRepository;
import com.nextgenmanager.nextgenmanager.items.model.InventoryItem;
import com.nextgenmanager.nextgenmanager.items.model.ProductInventorySettings;
import com.nextgenmanager.nextgenmanager.production.enums.WorkOrderStatus;
import com.nextgenmanager.nextgenmanager.production.model.WorkOrder;
import com.nextgenmanager.nextgenmanager.production.model.WorkOrderMaterial;
import com.nextgenmanager.nextgenmanager.production.repository.workorder.WorkOrderMaterialRepository;
import com.nextgenmanager.nextgenmanager.production.service.scheduling.engine.MaterialLedger;
import com.nextgenmanager.nextgenmanager.purchase.model.PurchaseOrder;
import com.nextgenmanager.nextgenmanager.purchase.model.PurchaseOrderItem;
import com.nextgenmanager.nextgenmanager.purchase.model.PurchaseOrderStatus;
import com.nextgenmanager.nextgenmanager.purchase.repository.PurchaseOrderRepository;
import com.nextgenmanager.nextgenmanager.purchase.requisition.model.PurchaseRequisitionApprovalStatus;
import com.nextgenmanager.nextgenmanager.purchase.requisition.model.PurchaseRequisitionItem;
import com.nextgenmanager.nextgenmanager.purchase.requisition.model.PurchaseRequisitionItemStatus;
import com.nextgenmanager.nextgenmanager.purchase.requisition.model.PurchaseRequisitionStatus;
import com.nextgenmanager.nextgenmanager.purchase.requisition.repository.PurchaseRequisitionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * Reads what material is coming into a {@link MaterialLedger}, and what an order still needs.
 *
 * <p>Supply, per item, earliest first: free stock now; open purchase orders (sent or partly
 * received) on their expected delivery; approved requisitions not yet ordered, in the item's
 * lead time; and beyond that, the item's lead time. Other scheduled orders' unreserved needs
 * are claimed first, earliest start first, so an order being planned only sees what they leave.
 *
 * <p>Free stock is the item's available quantity, which already leaves out what is reserved,
 * so an order whose material request stores has approved is not counted twice.
 */
@Component
public class MaterialSupply {

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");
    private static final double EPSILON = 1e-9;

    private static final List<PurchaseOrderStatus> OPEN_PURCHASE_ORDERS =
            List.of(PurchaseOrderStatus.SENT, PurchaseOrderStatus.PARTIALLY_RECEIVED);

    private static final List<PurchaseRequisitionStatus> CLOSED_REQUISITIONS =
            List.of(PurchaseRequisitionStatus.CLOSED, PurchaseRequisitionStatus.CANCELLED);

    private static final Set<InventoryApprovalStatus> RESERVED =
            EnumSet.of(InventoryApprovalStatus.APPROVED, InventoryApprovalStatus.PARTIAL);

    @Autowired
    private WorkOrderMaterialRepository workOrderMaterialRepository;

    @Autowired
    private InventoryRequestRepository inventoryRequestRepository;

    @Autowired
    private PurchaseOrderRepository purchaseOrderRepository;

    @Autowired
    private PurchaseRequisitionRepository purchaseRequisitionRepository;

    /** What one material of an order still needs to be brought to the floor. */
    public record Need(WorkOrderMaterial material, InventoryItem item, double quantity) {
    }

    /**
     * What each material of an order still needs: what it requires, less what has been issued
     * to it or reserved for it by an approved material request, whichever is more.
     */
    public List<Need> needs(List<WorkOrderMaterial> materials) {
        Map<Long, InventoryRequest> requests = requests(materials);
        List<Need> needs = new ArrayList<>();
        for (WorkOrderMaterial material : materials) {
            if (material.getDeletedDate() != null || material.getComponent() == null) continue;
            double required = number(material.getNetRequiredQuantity());
            double covered = number(material.getIssuedQuantity());
            InventoryRequest request = material.getInventoryRequestId() == null ? null
                    : requests.get(material.getInventoryRequestId());
            if (request != null && RESERVED.contains(request.getApprovalStatus())) {
                covered = Math.max(covered, number(request.getApprovedQuantity() != null
                        ? request.getApprovedQuantity() : request.getRequestedQuantity()));
            }
            double left = required - covered;
            if (left > EPSILON) needs.add(new Need(material, material.getComponent(), left));
        }
        return needs;
    }

    /**
     * The ledger for every item {@code orders} need, with what other scheduled orders (not
     * among {@code orders}) still need already taken from it.
     */
    public MaterialLedger load(Collection<WorkOrder> orders, Map<Integer, List<WorkOrderMaterial>> materialsByOrder,
                               LocalDateTime now) {
        MaterialLedger ledger = new MaterialLedger();
        Map<Integer, InventoryItem> items = new HashMap<>();
        for (WorkOrder wo : orders) {
            for (WorkOrderMaterial m : materialsByOrder.getOrDefault(wo.getId(), List.of())) {
                if (m.getDeletedDate() == null && m.getComponent() != null) {
                    items.putIfAbsent(m.getComponent().getInventoryItemId(), m.getComponent());
                }
            }
        }
        if (items.isEmpty()) return ledger;

        // Free stock, now.
        for (InventoryItem item : items.values()) {
            ProductInventorySettings settings = item.getProductInventorySettings();
            if (settings != null) ledger.supply(item.getInventoryItemId(), now, Math.max(settings.getAvailableQuantity(), 0), "stock");
        }

        // Open purchase orders, on their expected delivery. One already overdue may come any time.
        for (PurchaseOrderItem line : purchaseOrderRepository.findLinesForItems(items.keySet(), OPEN_PURCHASE_ORDERS)) {
            double open = line.getQuantityOrdered() - line.getQuantityReceived();
            if (open <= EPSILON || line.getItem() == null) continue;
            PurchaseOrder po = line.getPurchaseOrder();
            Date expected = po.getExpectedDeliveryDate() != null ? po.getExpectedDeliveryDate() : line.getRequiredByDate();
            InventoryItem item = items.get(line.getItem().getInventoryItemId());
            LocalDateTime arrives = expected != null ? toLocal(expected) : leadTimeFrom(now, item);
            String source = po.getPurchaseOrderNumber();
            if (arrives == null || arrives.isBefore(now)) {
                source += arrives == null ? ", no delivery date" : ", overdue";
                arrives = now;
            }
            ledger.supply(line.getItem().getInventoryItemId(), arrives, open, source);
        }

        // Approved requisitions not yet on a purchase order: ordered now, here in the lead time.
        for (PurchaseRequisitionItem line : purchaseRequisitionRepository.findOpenLinesForItems(items.keySet(),
                PurchaseRequisitionItemStatus.PENDING, PurchaseRequisitionApprovalStatus.APPROVED, CLOSED_REQUISITIONS)) {
            if (line.getItem() == null || line.getQuantity() <= EPSILON) continue;
            InventoryItem item = items.get(line.getItem().getInventoryItemId());
            LocalDateTime arrives = leadTimeFrom(now, item);
            if (arrives == null) {
                Date wanted = line.getRequiredByDate() != null ? line.getRequiredByDate() : line.getRequisition().getRequiredByDate();
                if (wanted == null) continue;
                arrives = later(toLocal(wanted), now);
            }
            ledger.supply(line.getItem().getInventoryItemId(), arrives, line.getQuantity(),
                    "requisition " + line.getRequisition().getPrNumber());
        }

        // Beyond all of that, the item can still be bought in its lead time.
        for (InventoryItem item : items.values()) {
            LocalDateTime arrives = leadTimeFrom(now, item);
            if (arrives != null) ledger.fallback(item.getInventoryItemId(), arrives, leadTimeLabel(item));
        }

        // Other scheduled orders claim first, earliest start first.
        Set<Integer> planning = new HashSet<>();
        orders.forEach(wo -> planning.add(wo.getId()));
        List<WorkOrderMaterial> claims = new ArrayList<>(workOrderMaterialRepository.findScheduledDemandForItems(
                items.keySet(), List.of(WorkOrderStatus.COMPLETED, WorkOrderStatus.CLOSED,
                        WorkOrderStatus.CANCELLED, WorkOrderStatus.SHORT_CLOSED)));
        claims.removeIf(m -> planning.contains(m.getWorkOrder().getId()));
        claims.sort(Comparator.comparing((WorkOrderMaterial m) -> m.getWorkOrder().getPlannedStartDate(),
                Comparator.nullsLast(Comparator.naturalOrder())));
        for (Need need : needs(claims)) ledger.allocate(need.item().getInventoryItemId(), need.quantity());

        return ledger;
    }

    private Map<Long, InventoryRequest> requests(List<WorkOrderMaterial> materials) {
        Set<Long> ids = new HashSet<>();
        materials.forEach(m -> { if (m.getInventoryRequestId() != null) ids.add(m.getInventoryRequestId()); });
        if (ids.isEmpty()) return Map.of();
        Map<Long, InventoryRequest> byId = new HashMap<>();
        inventoryRequestRepository.findAllById(ids).forEach(r -> byId.put(r.getId(), r));
        return byId;
    }

    /** Now plus the item's lead time in days, or null when it has none. */
    static LocalDateTime leadTimeFrom(LocalDateTime now, InventoryItem item) {
        double days = leadTimeDays(item);
        return days > 0 ? now.plusMinutes(Math.round(days * 24 * 60)) : null;
    }

    static String leadTimeLabel(InventoryItem item) {
        double days = leadTimeDays(item);
        String shown = days == Math.rint(days) ? String.valueOf((long) days) : String.valueOf(days);
        return "lead time " + shown + (days == 1 ? " day" : " days");
    }

    private static double leadTimeDays(InventoryItem item) {
        return item == null || item.getProductInventorySettings() == null ? 0
                : item.getProductInventorySettings().getLeadTime();
    }

    private static double number(BigDecimal value) {
        return value == null ? 0 : value.doubleValue();
    }

    private static LocalDateTime later(LocalDateTime a, LocalDateTime b) {
        return a.isAfter(b) ? a : b;
    }

    private static LocalDateTime toLocal(Date date) {
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(date.getTime()), IST).truncatedTo(ChronoUnit.MINUTES);
    }
}
