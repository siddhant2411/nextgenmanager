package com.nextgenmanager.nextgenmanager.Inventory.service;

import com.nextgenmanager.nextgenmanager.Inventory.dto.MaterialRequestRowDto;
import com.nextgenmanager.nextgenmanager.Inventory.dto.MaterialRequestSummaryDto;
import com.nextgenmanager.nextgenmanager.Inventory.model.InventoryApprovalStatus;
import com.nextgenmanager.nextgenmanager.Inventory.model.InventoryBookingApproval;
import com.nextgenmanager.nextgenmanager.Inventory.model.InventoryInstance;
import com.nextgenmanager.nextgenmanager.Inventory.model.InventoryInstanceStatus;
import com.nextgenmanager.nextgenmanager.Inventory.model.InventoryRequest;
import com.nextgenmanager.nextgenmanager.Inventory.model.InventoryRequestSource;
import com.nextgenmanager.nextgenmanager.Inventory.repository.InventoryBookingApprovalRepository;
import com.nextgenmanager.nextgenmanager.Inventory.repository.InventoryRequestRepository;
import com.nextgenmanager.nextgenmanager.items.model.InventoryItem;
import com.nextgenmanager.nextgenmanager.production.model.WorkOrder;
import com.nextgenmanager.nextgenmanager.production.repository.workorder.WorkOrderRepository;
import com.nextgenmanager.nextgenmanager.sales.model.SalesOrder;
import com.nextgenmanager.nextgenmanager.sales.repository.SalesOrderRepository;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Read model for the stores' material request register.
 *
 * <p>The stores used to see only what was still waiting: a request disappeared from the screen the
 * moment it was approved or rejected, so nobody could look up what was issued last week or why
 * something was turned down. This lists every request, in whatever state, filtered by status,
 * source, item or reference, and date.
 *
 * <p>Read-only. Approving and rejecting stay where they were -- {@link WorkOrderMaterialRequestService}
 * for a work order's request, {@link InventoryInstanceService} for one keyed by hand.
 */
@Service
@RequiredArgsConstructor
public class MaterialRequestRegisterService {

    /** Which states to list. OPEN is the working queue: anything the stores have not finished with. */
    public enum StatusFilter { OPEN, PENDING, PARTIAL, APPROVED, REJECTED, ALL }

    /**
     * Which requests to list. STORES is everything the stores act on, which leaves out a sales
     * order's request: that is the order's own stock reservation, settled by its pick and challan.
     * It can be listed by asking for SALES_ORDER, where it is shown but cannot be acted on.
     */
    public enum SourceFilter { STORES, WORK_ORDER, MANUAL, REORDER, SALES_ORDER }

    private static final int MAX_PAGE_SIZE = 100;

    private final InventoryRequestRepository requestRepository;
    private final InventoryBookingApprovalRepository bookingApprovalRepository;
    private final WorkOrderRepository workOrderRepository;
    private final SalesOrderRepository salesOrderRepository;
    private final WarehouseService warehouseService;

    @Transactional(readOnly = true)
    public Page<MaterialRequestRowDto> search(StatusFilter status, SourceFilter source, String text,
                                              LocalDate from, LocalDate to, int page, int size) {
        StatusFilter st = status != null ? status : StatusFilter.OPEN;
        SourceFilter src = source != null ? source : SourceFilter.STORES;

        // The queue is worked oldest first; history is read newest first.
        boolean queue = st == StatusFilter.OPEN || st == StatusFilter.PENDING || st == StatusFilter.PARTIAL;
        Sort sort = Sort.by(queue ? Sort.Direction.ASC : Sort.Direction.DESC, "requestedDate")
                .and(Sort.by(Sort.Direction.DESC, "id"));
        PageRequest pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE), sort);

        Page<InventoryRequest> found = requestRepository.findAll(matching(st, src, text, from, to), pageable);
        return toRows(found);
    }

    @Transactional(readOnly = true)
    public MaterialRequestSummaryDto summary() {
        Date weekAgo = Date.from(LocalDate.now().minusDays(7).atStartOfDay(ZoneId.systemDefault()).toInstant());

        long waiting = requestRepository.count(matching(StatusFilter.PENDING, SourceFilter.STORES, null, null, null));
        long partial = requestRepository.count(matching(StatusFilter.PARTIAL, SourceFilter.STORES, null, null, null));
        long approved = requestRepository.count(
                matching(StatusFilter.APPROVED, SourceFilter.STORES, null, null, null).and(decidedSince(weekAgo)));
        long rejected = requestRepository.count(
                matching(StatusFilter.REJECTED, SourceFilter.STORES, null, null, null).and(decidedSince(weekAgo)));
        long reservations = requestRepository.count(
                matching(StatusFilter.PENDING, SourceFilter.SALES_ORDER, null, null, null));

        Date oldest = requestRepository.findAll(
                        matching(StatusFilter.OPEN, SourceFilter.STORES, null, null, null),
                        PageRequest.of(0, 1, Sort.by(Sort.Direction.ASC, "requestedDate")))
                .stream().findFirst().map(InventoryRequest::getRequestedDate).orElse(null);

        return new MaterialRequestSummaryDto(waiting, partial, approved, rejected, reservations, oldest);
    }

    // ── Filtering ─────────────────────────────────────────────────────────────────────────

    private static Specification<InventoryRequest> matching(StatusFilter status, SourceFilter source,
                                                             String text, LocalDate from, LocalDate to) {
        return (root, query, cb) -> {
            List<Predicate> all = new ArrayList<>();

            Path<InventoryApprovalStatus> state = root.get("approvalStatus");
            switch (status) {
                case OPEN -> all.add(state.in(InventoryApprovalStatus.PENDING, InventoryApprovalStatus.PARTIAL));
                case PENDING -> all.add(cb.equal(state, InventoryApprovalStatus.PENDING));
                case PARTIAL -> all.add(cb.equal(state, InventoryApprovalStatus.PARTIAL));
                case APPROVED -> all.add(cb.equal(state, InventoryApprovalStatus.APPROVED));
                case REJECTED -> all.add(cb.equal(state, InventoryApprovalStatus.REJECTED));
                case ALL -> { }
            }

            Path<InventoryRequestSource> raisedBy = root.get("requestSource");
            switch (source) {
                // Older rows carry no source at all; they were keyed by hand and belong to the stores.
                case STORES -> all.add(cb.or(cb.isNull(raisedBy),
                        cb.notEqual(raisedBy, InventoryRequestSource.SALES_ORDER)));
                case WORK_ORDER -> all.add(cb.equal(raisedBy, InventoryRequestSource.WORK_ORDER));
                case MANUAL -> all.add(cb.or(cb.isNull(raisedBy),
                        cb.equal(raisedBy, InventoryRequestSource.MANUAL)));
                case REORDER -> all.add(cb.equal(raisedBy, InventoryRequestSource.REORDER));
                case SALES_ORDER -> all.add(cb.equal(raisedBy, InventoryRequestSource.SALES_ORDER));
            }

            if (text != null && !text.isBlank()) {
                String like = "%" + text.trim().toLowerCase() + "%";
                Join<InventoryRequest, InventoryItem> item = root.join("inventoryItem", JoinType.LEFT);
                all.add(cb.or(
                        cb.like(cb.lower(item.get("itemCode")), like),
                        cb.like(cb.lower(item.get("name")), like),
                        // A work order's request carries the work order number in its reference.
                        cb.like(cb.lower(root.get("referenceNumber")), like)));
            }

            if (from != null) {
                all.add(cb.greaterThanOrEqualTo(root.get("requestedDate"), startOf(from)));
            }
            if (to != null) {
                all.add(cb.lessThan(root.get("requestedDate"), startOf(to.plusDays(1))));
            }

            return cb.and(all.toArray(new Predicate[0]));
        };
    }

    /** Decided on or after the given moment. A row decided before decisions were dated falls back to when it was raised. */
    private static Specification<InventoryRequest> decidedSince(Date since) {
        return (root, query, cb) -> cb.greaterThanOrEqualTo(
                cb.coalesce(root.<Date>get("approvedDate"), root.<Date>get("requestedDate")), since);
    }

    private static Date startOf(LocalDate day) {
        return Date.from(day.atStartOfDay(ZoneId.systemDefault()).toInstant());
    }

    // ── Rows ──────────────────────────────────────────────────────────────────────────────

    private Page<MaterialRequestRowDto> toRows(Page<InventoryRequest> found) {
        // One lookup per kind of source document for the whole page, not one per row.
        Set<Integer> workOrderIds = found.stream()
                .filter(r -> r.getRequestSource() == InventoryRequestSource.WORK_ORDER && r.getSourceId() != null)
                .map(r -> r.getSourceId().intValue())
                .collect(Collectors.toSet());
        Set<Long> salesOrderIds = found.stream()
                .filter(r -> r.getRequestSource() == InventoryRequestSource.SALES_ORDER && r.getSourceId() != null)
                .map(InventoryRequest::getSourceId)
                .collect(Collectors.toSet());

        Map<Integer, WorkOrder> workOrders = new HashMap<>();
        if (!workOrderIds.isEmpty()) {
            workOrderRepository.findAllById(workOrderIds).forEach(wo -> workOrders.put(wo.getId(), wo));
        }
        Map<Long, SalesOrder> salesOrders = new HashMap<>();
        if (!salesOrderIds.isEmpty()) {
            salesOrderRepository.findAllById(salesOrderIds).forEach(so -> salesOrders.put(so.getId(), so));
        }
        String defaultWarehouse = defaultWarehouseCode();

        return found.map(r -> toRow(r, workOrders, salesOrders, defaultWarehouse));
    }

    private MaterialRequestRowDto toRow(InventoryRequest r, Map<Integer, WorkOrder> workOrders,
                                        Map<Long, SalesOrder> salesOrders, String defaultWarehouse) {
        InventoryRequestSource source = r.getRequestSource();
        InventoryApprovalStatus status = r.getApprovalStatus();
        InventoryItem item = r.getInventoryItem();

        String sourceNumber = null;
        String warehouseCode = null;
        if (source == InventoryRequestSource.WORK_ORDER && r.getSourceId() != null) {
            WorkOrder wo = workOrders.get(r.getSourceId().intValue());
            if (wo != null) {
                sourceNumber = wo.getWorkOrderNumber();
                // Material is wanted where the work order draws it from.
                warehouseCode = Objects.requireNonNullElse(wo.stockWarehouseCode(), defaultWarehouse);
            }
        } else if (source == InventoryRequestSource.SALES_ORDER && r.getSourceId() != null) {
            SalesOrder so = salesOrders.get(r.getSourceId());
            if (so != null) {
                sourceNumber = so.getOrderNumber();
                warehouseCode = so.getDispatchWarehouse() != null
                        ? so.getDispatchWarehouse().getCode() : defaultWarehouse;
            }
        }

        Quantities q = quantitiesOf(r);
        boolean open = status == InventoryApprovalStatus.PENDING || status == InventoryApprovalStatus.PARTIAL;
        boolean storesRequest = source != InventoryRequestSource.SALES_ORDER;

        BigDecimal available = item != null && item.getProductInventorySettings() != null
                ? BigDecimal.valueOf(item.getProductInventorySettings().getAvailableQuantity())
                : null;
        boolean stockShort = open && storesRequest && available != null
                && available.compareTo(q.remaining()) < 0;

        Decision decision = decisionOf(r);

        return new MaterialRequestRowDto(
                r.getId(),
                r.getReferenceNumber(),
                source,
                r.getSourceId(),
                sourceNumber,
                item != null ? item.getInventoryItemId() : null,
                item != null ? item.getItemCode() : null,
                item != null ? item.getName() : null,
                item != null && item.getUom() != null ? item.getUom().name() : null,
                q.requested(),
                q.issued(),
                q.remaining(),
                available,
                stockShort,
                warehouseCode,
                status,
                r.getRequestedBy(),
                r.getRequestedDate(),
                r.getRequestRemarks(),
                decision.by(),
                decision.on(),
                r.getRejectionReason() != null ? r.getRejectionReason() : decision.rejectedBecause(),
                open && storesRequest);
    }

    private record Quantities(BigDecimal requested, BigDecimal issued, BigDecimal remaining) {}

    /**
     * A work order's request states its quantities. Every other kind is a set of stock units, so
     * its quantities are read off them: a unit still PENDING is one the stores do not have yet.
     */
    private static Quantities quantitiesOf(InventoryRequest r) {
        if (r.getRequestedQuantity() != null) {
            BigDecimal requested = r.getRequestedQuantity();
            BigDecimal issued = r.getApprovedQuantity() != null ? r.getApprovedQuantity() : BigDecimal.ZERO;
            return new Quantities(requested, issued, requested.subtract(issued).max(BigDecimal.ZERO));
        }
        List<InventoryInstance> units = r.getRequestedInstances() != null ? r.getRequestedInstances() : List.of();
        BigDecimal requested = BigDecimal.ZERO;
        BigDecimal notHere = BigDecimal.ZERO;
        for (InventoryInstance unit : units) {
            if (unit.getQuantity() == null || unit.getInventoryInstanceStatus() == InventoryInstanceStatus.DESTROYED) {
                continue;
            }
            requested = requested.add(unit.getQuantity());
            if (unit.getInventoryInstanceStatus() == InventoryInstanceStatus.PENDING) {
                notHere = notHere.add(unit.getQuantity());
            }
        }
        return new Quantities(requested, requested.subtract(notHere), notHere);
    }

    private record Decision(String by, Date on, String rejectedBecause) {}

    /**
     * Who decided a request and when. A work order's request records that on itself. One keyed by
     * hand used to record it only as a separate approval row, so an older decided request with
     * nothing on it is looked up there.
     */
    private Decision decisionOf(InventoryRequest r) {
        if (r.getApprovedBy() != null || r.getApprovedDate() != null) {
            return new Decision(r.getApprovedBy(), r.getApprovedDate(), null);
        }
        InventoryApprovalStatus status = r.getApprovalStatus();
        if (status != InventoryApprovalStatus.APPROVED && status != InventoryApprovalStatus.REJECTED) {
            return new Decision(null, null, null);
        }
        InventoryBookingApproval recorded = bookingApprovalRepository
                .findFirstByInventoryRequestIdOrderByApprovalDateDesc(r.getId())
                .orElse(null);
        if (recorded == null) return new Decision(null, null, null);
        return new Decision(recorded.getApprovedBy(), recorded.getApprovalDate(),
                status == InventoryApprovalStatus.REJECTED ? recorded.getApprovalRemarks() : null);
    }

    /** Null when no default warehouse is set up; the register is still worth showing without it. */
    private String defaultWarehouseCode() {
        try {
            return warehouseService.resolveDefaultWarehouse().getCode();
        } catch (IllegalStateException e) {
            return null;
        }
    }
}
