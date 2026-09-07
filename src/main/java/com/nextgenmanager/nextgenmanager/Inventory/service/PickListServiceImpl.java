package com.nextgenmanager.nextgenmanager.Inventory.service;

import com.nextgenmanager.nextgenmanager.Inventory.dto.*;
import com.nextgenmanager.nextgenmanager.Inventory.model.*;
import com.nextgenmanager.nextgenmanager.Inventory.repository.InventoryInstanceRepository;
import com.nextgenmanager.nextgenmanager.Inventory.repository.PickListRepository;
import com.nextgenmanager.nextgenmanager.items.model.InventoryItem;
import com.nextgenmanager.nextgenmanager.items.model.ProductInventorySettings;
import com.nextgenmanager.nextgenmanager.sales.model.SalesOrder;
import com.nextgenmanager.nextgenmanager.sales.model.SalesOrderItem;
import com.nextgenmanager.nextgenmanager.sales.model.SalesOrderStatus;
import com.nextgenmanager.nextgenmanager.sales.repository.SalesOrderRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Pick lists.
 *
 * <p>Picking does not move any counter. Stock is reserved when the sales order is approved; this
 * decides which physical units satisfy that reservation and records who took them. Keeping it
 * counter-free means the V165/V166 invariant cannot be disturbed by picking, and it leaves the
 * delivery note as the single place stock is actually consumed — until the next step moves that
 * consumption onto a confirmed pick.
 */
@Service
@RequiredArgsConstructor
public class PickListServiceImpl implements PickListService {

    private static final Logger logger = LoggerFactory.getLogger(PickListServiceImpl.class);

    private final PickListRepository pickListRepository;
    private final InventoryInstanceRepository inventoryInstanceRepository;
    private final SalesOrderRepository salesOrderRepository;
    private final WarehouseService warehouseService;
    private final PickListNumberGenerator numberGenerator;

    // ─── Reads ────────────────────────────────────────────────────────────────

    @Override
    public List<PickListDto> list(PickListStatus status, Long salesOrderId) {
        List<PickList> rows;
        if (status != null)            rows = pickListRepository.findLiveByStatus(status);
        else if (salesOrderId != null) rows = pickListRepository.findLiveBySalesOrder(salesOrderId);
        else                           rows = pickListRepository.findAllLive();
        return rows.stream().map(this::toDto).toList();
    }

    @Override
    public PickListDto get(Long id) {
        return toDto(load(id));
    }

    // ─── Lifecycle ────────────────────────────────────────────────────────────

    @Override
    @Transactional
    public PickListDto createFromSalesOrder(PickListCreateRequest request) {
        SalesOrder so = salesOrderRepository.findById(request.salesOrderId())
                .orElseThrow(() -> new IllegalArgumentException(
                        "Sales order not found: " + request.salesOrderId()));

        // Same gate the delivery note applies. Picking goods for an order nobody has approved puts
        // stock on a trolley against a document that may never become an order at all.
        if (so.getStatus() == SalesOrderStatus.DRAFT || so.getStatus() == SalesOrderStatus.CANCELLED) {
            throw new IllegalStateException(
                    "Cannot pick for " + so.getOrderNumber() + ": it is " + so.getStatus());
        }

        Warehouse warehouse = warehouseService.resolveByCodeOrDefault(request.warehouseCode());

        // Build every line before drawing a number: the generator commits separately, so a number
        // requested ahead of validation is spent even when this rolls back.
        List<PickListLine> lines = new ArrayList<>();
        for (SalesOrderItem soItem : so.getItems()) {
            InventoryItem item = soItem.getInventoryItem();
            if (item == null) continue;

            BigDecimal ordered = soItem.getQty() != null ? soItem.getQty() : BigDecimal.ZERO;
            BigDecimal already = pickListRepository.sumAlreadyOnPicks(so.getId(), item.getInventoryItemId());
            BigDecimal remaining = ordered.subtract(already != null ? already : BigDecimal.ZERO);

            // Nothing left to pick for this line — it is already covered by other live picks.
            if (remaining.signum() <= 0) continue;

            PickListLine line = new PickListLine();
            line.setInventoryItem(item);
            line.setSalesOrderItem(soItem);
            line.setQuantityToPick(remaining);
            line.setQuantityPicked(BigDecimal.ZERO);
            lines.add(line);
        }

        if (lines.isEmpty()) {
            throw new IllegalStateException(
                    "Nothing left to pick on " + so.getOrderNumber() + ": every line is already on a pick list");
        }

        PickList pick = new PickList();
        pick.setPickNumber(numberGenerator.next());
        pick.setSalesOrder(so);
        pick.setWarehouse(warehouse);
        pick.setStatus(PickListStatus.DRAFT);
        pick.setRemarks(request.remarks());
        pick.setCreatedBy(currentUser());
        for (PickListLine line : lines) {
            line.setPickList(pick);
            pick.getLines().add(line);
        }

        PickList saved = pickListRepository.save(pick);
        logger.info("Pick list {} created for {} from {} ({} lines)",
                saved.getPickNumber(), so.getOrderNumber(), warehouse.getCode(), lines.size());
        return toDto(saved);
    }

    @Override
    @Transactional
    public PickListDto release(Long id) {
        PickList pick = load(id);
        requireStatus(pick, PickListStatus.DRAFT, "release");
        pick.setStatus(PickListStatus.RELEASED);
        pick.setReleasedDate(new Date());
        pick.setUpdatedDate(new Date());
        logger.info("Pick list {} released to the floor", pick.getPickNumber());
        return toDto(pickListRepository.save(pick));
    }

    @Override
    @Transactional
    public PickListDto confirm(Long id, PickConfirmRequest request) {
        PickList pick = load(id);
        if (pick.getStatus() != PickListStatus.RELEASED && pick.getStatus() != PickListStatus.DRAFT) {
            throw new IllegalStateException(String.format(
                    "Cannot confirm %s: it is %s", pick.getPickNumber(), pick.getStatus()));
        }

        for (PickListLine line : pick.getLines()) {
            PickConfirmRequest.Line asked = lineRequest(request, line);
            List<Long> instanceIds = asked != null && asked.instanceIds() != null
                    ? asked.instanceIds() : List.of();

            boolean tracked = isTracked(line.getInventoryItem());
            if (tracked && instanceIds.isEmpty()) {
                throw new IllegalArgumentException(String.format(
                        "%s is batch or serial tracked — name the instances picked for it",
                        line.getInventoryItem().getItemCode()));
            }

            List<InventoryInstance> instances = resolveInstances(pick, line, instanceIds);

            BigDecimal picked = asked != null && asked.quantityPicked() != null
                    ? asked.quantityPicked()
                    : (tracked ? totalQuantity(instances) : line.getQuantityToPick());

            if (picked.signum() < 0 || picked.compareTo(line.getQuantityToPick()) > 0) {
                throw new IllegalArgumentException(String.format(
                        "Picked quantity for %s must be between 0 and the requested %s",
                        line.getInventoryItem().getItemCode(), line.getQuantityToPick().toPlainString()));
            }
            if (tracked && totalQuantity(instances).compareTo(picked) != 0) {
                throw new IllegalArgumentException(String.format(
                        "%s: the instances named add up to %s but the picked quantity says %s",
                        line.getInventoryItem().getItemCode(),
                        totalQuantity(instances).toPlainString(), picked.toPlainString()));
            }

            for (InventoryInstance inst : instances) {
                inst.setPickListLine(line);
                inventoryInstanceRepository.save(inst);
            }
            line.setQuantityPicked(picked);
            if (asked != null && asked.quantityPicked() != null
                    && picked.compareTo(line.getQuantityToPick()) < 0) {
                logger.info("Short pick on {} line {}: {} of {}", pick.getPickNumber(),
                        line.getInventoryItem().getItemCode(), picked, line.getQuantityToPick());
            }
        }

        pick.setStatus(PickListStatus.PICKED);
        pick.setPickedDate(new Date());
        pick.setPickedBy(request != null && request.pickedBy() != null ? request.pickedBy() : currentUser());
        if (request != null && request.remarks() != null && !request.remarks().isBlank()) {
            pick.setRemarks(request.remarks());
        }
        pick.setUpdatedDate(new Date());
        logger.info("Pick list {} picked by {}", pick.getPickNumber(), pick.getPickedBy());
        return toDto(pickListRepository.save(pick));
    }

    @Override
    @Transactional
    public void cancel(Long id) {
        PickList pick = load(id);
        if (pick.getStatus() == PickListStatus.CANCELLED) {
            throw new IllegalStateException("Pick list " + pick.getPickNumber() + " is already cancelled");
        }
        // Releasing the allocation on a dispatched pick would put units back on the shelf that are
        // on a lorry, and strip the delivery note of the record of what it shipped.
        if (pick.getStatus() == PickListStatus.DISPATCHED) {
            String on = pick.getDeliveryNote() != null
                    ? pick.getDeliveryNote().getDeliveryNoteNo() : "a delivery note";
            throw new IllegalStateException(String.format(
                    "Cannot cancel %s: it was shipped on %s. Reverse the delivery note instead.",
                    pick.getPickNumber(), on));
        }

        // Release every allocation, or the stock stays claimed by a pick that no longer exists.
        for (PickListLine line : pick.getLines()) {
            for (InventoryInstance inst : inventoryInstanceRepository.findByPickListLineId(line.getId())) {
                inst.setPickListLine(null);
                inventoryInstanceRepository.save(inst);
            }
            line.setQuantityPicked(BigDecimal.ZERO);
        }

        pick.setStatus(PickListStatus.CANCELLED);
        pick.setUpdatedDate(new Date());
        pickListRepository.save(pick);
        logger.info("Pick list {} cancelled and its allocations released", pick.getPickNumber());
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private PickList load(Long id) {
        return pickListRepository.findLiveById(id)
                .orElseThrow(() -> new IllegalArgumentException("Pick list not found: " + id));
    }

    private void requireStatus(PickList pick, PickListStatus expected, String action) {
        if (pick.getStatus() != expected) {
            throw new IllegalStateException(String.format(
                    "Cannot %s %s: it is %s, not %s", action, pick.getPickNumber(), pick.getStatus(), expected));
        }
    }

    private boolean isTracked(InventoryItem item) {
        ProductInventorySettings s = item.getProductInventorySettings();
        return s != null && (s.isBatchTracked() || s.isSerialTracked());
    }

    private PickConfirmRequest.Line lineRequest(PickConfirmRequest request, PickListLine line) {
        if (request == null || request.lines() == null) return null;
        return request.lines().stream()
                .filter(l -> l.lineId() != null && l.lineId().equals(line.getId()))
                .findFirst().orElse(null);
    }

    /**
     * Every named instance must be the right item, sitting in the warehouse being picked, still
     * available, and not already claimed by another pick. Checked before anything is written so a
     * bad line cannot half-allocate.
     */
    private List<InventoryInstance> resolveInstances(PickList pick, PickListLine line, List<Long> ids) {
        List<InventoryInstance> resolved = new ArrayList<>();
        for (Long instanceId : ids) {
            InventoryInstance inst = inventoryInstanceRepository.findById(instanceId)
                    .orElseThrow(() -> new IllegalArgumentException("Inventory instance not found: " + instanceId));

            if (inst.getInventoryItem() == null
                    || inst.getInventoryItem().getInventoryItemId() != line.getInventoryItem().getInventoryItemId()) {
                throw new IllegalArgumentException(String.format(
                        "Instance %d is not %s", instanceId, line.getInventoryItem().getItemCode()));
            }
            if (inst.getWarehouse() == null
                    || !inst.getWarehouse().getId().equals(pick.getWarehouse().getId())) {
                throw new IllegalArgumentException(String.format(
                        "Instance %d is not in %s", instanceId, pick.getWarehouse().getCode()));
            }
            if (inst.isConsumed() || inst.getInventoryInstanceStatus() == InventoryInstanceStatus.CONSUMED) {
                throw new IllegalArgumentException("Instance " + instanceId + " has already been consumed");
            }
            // Quality is a gate here too (phase H). Stock that failed inspection, or that is still
            // waiting for one, is physically on a shelf and must not walk out on a delivery note.
            if (inst.getQualityStatus() == QualityStatus.FAILED
                    || inst.getQualityStatus() == QualityStatus.PENDING_QC) {
                throw new IllegalArgumentException(String.format(
                        "Instance %d cannot be picked: its quality status is %s",
                        instanceId, inst.getQualityStatus()));
            }
            if (inst.getPickListLine() != null && !inst.getPickListLine().getId().equals(line.getId())) {
                throw new IllegalArgumentException(String.format(
                        "Instance %d is already allocated to pick %s", instanceId,
                        inst.getPickListLine().getPickList().getPickNumber()));
            }
            resolved.add(inst);
        }
        return resolved;
    }

    private BigDecimal totalQuantity(List<InventoryInstance> instances) {
        return instances.stream()
                .map(i -> i.getQuantity() != null ? i.getQuantity() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private String currentUser() {
        try {
            return SecurityContextHolder.getContext().getAuthentication().getName();
        } catch (Exception ignored) {
            return "system";
        }
    }

    private PickListDto toDto(PickList p) {
        List<PickListLineDto> lines = new ArrayList<>();
        for (PickListLine l : p.getLines()) {
            List<Long> instanceIds = inventoryInstanceRepository.findByPickListLineId(l.getId())
                    .stream().map(InventoryInstance::getId).toList();
            lines.add(new PickListLineDto(
                    l.getId(),
                    l.getInventoryItem().getInventoryItemId(),
                    l.getInventoryItem().getItemCode(),
                    l.getInventoryItem().getName(),
                    l.getSalesOrderItem() != null ? l.getSalesOrderItem().getId() : null,
                    l.getStorageLocation() != null ? l.getStorageLocation().getCode() : null,
                    l.getQuantityToPick(),
                    l.getQuantityPicked(),
                    instanceIds,
                    isTracked(l.getInventoryItem()),
                    l.getRemarks()));
        }
        return new PickListDto(
                p.getId(), p.getPickNumber(),
                p.getSalesOrder().getId(), p.getSalesOrder().getOrderNumber(),
                p.getWarehouse().getId(), p.getWarehouse().getCode(),
                p.getStatus(), p.getReleasedDate(), p.getPickedDate(),
                p.getPickedBy(), p.getRemarks(), p.getCreatedBy(),
                p.getDeliveryNote() != null ? p.getDeliveryNote().getId() : null,
                p.getDeliveryNote() != null ? p.getDeliveryNote().getDeliveryNoteNo() : null,
                lines);
    }
}
