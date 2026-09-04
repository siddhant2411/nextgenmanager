package com.nextgenmanager.nextgenmanager.quality.service;

import com.nextgenmanager.nextgenmanager.Inventory.model.GoodsReceiptNote;
import com.nextgenmanager.nextgenmanager.Inventory.repository.GoodsReceiptNoteRepository;
import com.nextgenmanager.nextgenmanager.items.model.InventoryItem;
import com.nextgenmanager.nextgenmanager.items.repository.InventoryItemRepository;
import com.nextgenmanager.nextgenmanager.production.enums.QaResult;
import com.nextgenmanager.nextgenmanager.production.model.WorkOrder;
import com.nextgenmanager.nextgenmanager.production.model.WorkOrderOperation;
import com.nextgenmanager.nextgenmanager.production.repository.workorder.WorkOrderRepository;
import com.nextgenmanager.nextgenmanager.production.repository.workorder.WorkOrderOperationRepository;
import com.nextgenmanager.nextgenmanager.quality.dto.*;
import com.nextgenmanager.nextgenmanager.quality.model.InspectionLot;
import com.nextgenmanager.nextgenmanager.quality.model.InspectionLotStatus;
import com.nextgenmanager.nextgenmanager.quality.model.InspectionResult;
import com.nextgenmanager.nextgenmanager.quality.model.InspectionSource;
import com.nextgenmanager.nextgenmanager.quality.repository.InspectionLotRepository;
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

@Service
@RequiredArgsConstructor
public class InspectionLotServiceImpl implements InspectionLotService {

    private static final Logger logger = LoggerFactory.getLogger(InspectionLotServiceImpl.class);

    private final InspectionLotRepository inspectionLotRepository;
    private final InventoryItemRepository inventoryItemRepository;
    private final WorkOrderRepository workOrderRepository;
    private final WorkOrderOperationRepository workOrderOperationRepository;
    private final GoodsReceiptNoteRepository goodsReceiptNoteRepository;
    private final InspectionLotNumberGenerator numberGenerator;

    // ─── Reads ────────────────────────────────────────────────────────────────

    @Override
    public List<InspectionLotDto> list(InspectionSource source, InspectionLotStatus status) {
        List<InspectionLot> rows;
        if (status != null)      rows = inspectionLotRepository.findLiveByStatus(status);
        else if (source != null) rows = inspectionLotRepository.findLiveBySource(source);
        else                     rows = inspectionLotRepository.findAllLive();
        return rows.stream().map(this::toDto).toList();
    }

    @Override
    public InspectionLotDto get(Long id) {
        return toDto(load(id));
    }

    @Override
    public List<InspectionLotDto> forWorkOrder(int workOrderId) {
        return inspectionLotRepository.findLiveByWorkOrder(workOrderId).stream()
                .map(this::toDto).toList();
    }

    // ─── Lifecycle ────────────────────────────────────────────────────────────

    @Override
    @Transactional
    public InspectionLotDto raise(InspectionLotCreateRequest request) {
        if (request.source() == null) {
            throw new IllegalArgumentException("An inspection has to say what it is inspecting");
        }
        if (request.quantityOffered() == null || request.quantityOffered().signum() <= 0) {
            throw new IllegalArgumentException("Quantity offered must be greater than zero");
        }

        // Everything is resolved and checked before a number is drawn: the generator commits in
        // its own transaction, so a number asked for ahead of validation is spent even when this
        // rolls back, leaving a permanent gap in the sequence.
        InspectionLot lot = new InspectionLot();
        lot.setSource(request.source());
        lot.setQuantityOffered(request.quantityOffered());
        lot.setRemarks(request.remarks());

        switch (request.source()) {
            case FINAL -> {
                WorkOrder workOrder = requireWorkOrder(request.workOrderId());
                lot.setWorkOrder(workOrder);
                lot.setInventoryItem(resolveItem(request.inventoryItemId(), workOrder));
            }
            case IN_PROCESS -> {
                WorkOrderOperation operation = requireOperation(request.workOrderOperationId());
                lot.setWorkOrderOperation(operation);
                lot.setWorkOrder(operation.getWorkOrder());
                lot.setInventoryItem(resolveItem(request.inventoryItemId(), operation.getWorkOrder()));
            }
            case INCOMING -> {
                GoodsReceiptNote grn = goodsReceiptNoteRepository.findById(requireId(
                                request.goodsReceiptNoteId(), "A goods receipt note"))
                        .orElseThrow(() -> new IllegalArgumentException(
                                "Goods receipt note not found: " + request.goodsReceiptNoteId()));
                lot.setGoodsReceiptNote(grn);
                lot.setInventoryItem(requireItem(request.inventoryItemId()));
            }
            case PACKAGE -> throw new IllegalArgumentException(
                    "Package inspection arrives with packing slips in phase J");
        }

        for (InspectionCheckLine check : safe(request.checks())) {
            lot.getResults().add(toResult(lot, check));
        }

        lot.setLotNumber(numberGenerator.next());
        lot.setStatus(InspectionLotStatus.PENDING);
        lot.setCreatedBy(currentUser());

        InspectionLot saved = inspectionLotRepository.save(lot);
        logger.info("Inspection {} raised: {} on {} ({} offered)", saved.getLotNumber(),
                saved.getSource(), documentLabel(saved), saved.getQuantityOffered());
        return toDto(saved);
    }

    @Override
    @Transactional
    public InspectionLotDto judge(Long id, InspectionJudgeRequest request) {
        InspectionLot lot = load(id);
        if (lot.getStatus() != InspectionLotStatus.PENDING) {
            throw new IllegalStateException(String.format(
                    "%s has already been judged: it is %s", lot.getLotNumber(), lot.getStatus()));
        }

        BigDecimal accepted = request.quantityAccepted() != null
                ? request.quantityAccepted() : BigDecimal.ZERO;
        BigDecimal rejected = request.quantityRejected() != null
                ? request.quantityRejected() : BigDecimal.ZERO;
        if (accepted.signum() < 0 || rejected.signum() < 0) {
            throw new IllegalArgumentException("Quantities cannot be negative");
        }
        if (accepted.add(rejected).compareTo(lot.getQuantityOffered()) > 0) {
            throw new IllegalArgumentException(String.format(
                    "%s: accepted %s plus rejected %s is more than the %s offered",
                    lot.getLotNumber(), accepted.toPlainString(), rejected.toPlainString(),
                    lot.getQuantityOffered().toPlainString()));
        }

        if (request.checks() != null && !request.checks().isEmpty()) {
            lot.getResults().clear();
            for (InspectionCheckLine check : request.checks()) {
                lot.getResults().add(toResult(lot, check));
            }
        }

        lot.setQuantityAccepted(accepted);
        lot.setQuantityRejected(rejected);
        lot.setInspectedBy(request.inspectedBy() != null ? request.inspectedBy() : currentUser());
        lot.setInspectedDate(new Date());
        if (request.remarks() != null && !request.remarks().isBlank()) {
            lot.setRemarks(request.remarks());
        }
        lot.setStatus(verdict(lot, accepted));
        lot.setUpdatedDate(new Date());

        InspectionLot saved = inspectionLotRepository.save(lot);
        logger.info("Inspection {} judged {} by {} ({} accepted, {} rejected)",
                saved.getLotNumber(), saved.getStatus(), saved.getInspectedBy(), accepted, rejected);
        return toDto(saved);
    }

    @Override
    @Transactional
    public InspectionLotDto waive(Long id, InspectionWaiverRequest request) {
        InspectionLot lot = load(id);
        if (lot.getStatus() == InspectionLotStatus.WAIVED) {
            throw new IllegalStateException(lot.getLotNumber() + " has already been waived");
        }
        if (lot.getStatus() == InspectionLotStatus.PASSED) {
            throw new IllegalStateException(
                    lot.getLotNumber() + " passed — there is nothing to waive");
        }
        if (request == null || request.reason() == null || request.reason().isBlank()) {
            throw new IllegalArgumentException(
                    "A waiver needs a reason: it is the record of why goods that failed went out");
        }

        lot.setStatus(InspectionLotStatus.WAIVED);
        lot.setWaivedBy(request.waivedBy() != null ? request.waivedBy() : currentUser());
        lot.setWaiverReason(request.reason());
        lot.setUpdatedDate(new Date());

        InspectionLot saved = inspectionLotRepository.save(lot);
        logger.warn("Inspection {} WAIVED by {}: {}", saved.getLotNumber(),
                saved.getWaivedBy(), saved.getWaiverReason());
        return toDto(saved);
    }

    @Override
    @Transactional
    public void cancel(Long id) {
        InspectionLot lot = load(id);
        if (lot.getStatus() != InspectionLotStatus.PENDING) {
            throw new IllegalStateException(String.format(
                    "Cannot cancel %s: it is %s, and a verdict is a record.",
                    lot.getLotNumber(), lot.getStatus()));
        }
        lot.setDeletedDate(new Date());
        lot.setUpdatedDate(new Date());
        inspectionLotRepository.save(lot);
        logger.info("Inspection {} cancelled before it was judged", lot.getLotNumber());
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    /**
     * A lot fails when a critical check failed, or when nothing was accepted. Non-critical
     * failures are recorded and reported: a scratch on a casting is worth knowing about and is not
     * on its own a reason to stop the goods.
     */
    private InspectionLotStatus verdict(InspectionLot lot, BigDecimal accepted) {
        boolean criticalFailure = lot.getResults().stream()
                .anyMatch(r -> r.isCritical() && r.getResult() == QaResult.FAIL);
        if (criticalFailure || accepted.signum() <= 0) return InspectionLotStatus.FAILED;
        return InspectionLotStatus.PASSED;
    }

    private InspectionResult toResult(InspectionLot lot, InspectionCheckLine check) {
        InspectionResult result = new InspectionResult();
        result.setInspectionLot(lot);
        result.setParameterName(check.parameterName());
        result.setParameterType(check.parameterType());
        result.setMinValue(check.minValue());
        result.setMaxValue(check.maxValue());
        result.setUnit(check.unit());
        result.setCritical(Boolean.TRUE.equals(check.critical()));
        result.setObservedValue(check.observedValue());
        result.setObservedText(check.observedText());
        result.setRemarks(check.remarks());

        // The inspector's own verdict wins where they gave one. Where they did not, a numeric
        // reading against a stated limit decides itself; anything else stays PENDING rather than
        // being guessed at.
        if (check.passed() != null) {
            result.setResult(check.passed() ? QaResult.PASS : QaResult.FAIL);
        } else {
            Boolean inSpec = result.withinSpecification();
            result.setResult(inSpec == null ? QaResult.PENDING
                    : inSpec ? QaResult.PASS : QaResult.FAIL);
        }
        return result;
    }

    private InspectionLot load(Long id) {
        return inspectionLotRepository.findLiveById(id)
                .orElseThrow(() -> new IllegalArgumentException("Inspection lot not found: " + id));
    }

    private WorkOrder requireWorkOrder(Integer workOrderId) {
        return workOrderRepository.findById(requireId(workOrderId, "A work order").intValue())
                .orElseThrow(() -> new IllegalArgumentException(
                        "Work order not found: " + workOrderId));
    }

    private WorkOrderOperation requireOperation(Long operationId) {
        return workOrderOperationRepository.findById(requireId(operationId, "An operation"))
                .orElseThrow(() -> new IllegalArgumentException(
                        "Work order operation not found: " + operationId));
    }

    private InventoryItem requireItem(Integer itemId) {
        InventoryItem item = inventoryItemRepository.findByActiveId(
                requireId(itemId, "An item").intValue());
        if (item == null) throw new IllegalArgumentException("Inventory item not found: " + itemId);
        return item;
    }

    /**
     * The item under inspection. Named explicitly when the caller knows it; otherwise taken from
     * the work order, which is unambiguous only while the order makes one thing.
     */
    private InventoryItem resolveItem(Integer itemId, WorkOrder workOrder) {
        if (itemId != null) return requireItem(itemId);
        if (workOrder != null && workOrder.activeLines().size() == 1) {
            InventoryItem item = workOrder.activeLines().get(0).getInventoryItem();
            if (item != null) return item;
        }
        throw new IllegalArgumentException(
                "Name the item being inspected: this work order makes more than one thing");
    }

    private <T extends Number> T requireId(T id, String what) {
        if (id == null) {
            throw new IllegalArgumentException(what + " must be named for this kind of inspection");
        }
        return id;
    }

    private String documentLabel(InspectionLot lot) {
        if (lot.getWorkOrder() != null) return lot.getWorkOrder().getWorkOrderNumber();
        if (lot.getGoodsReceiptNote() != null) return "GRN " + lot.getGoodsReceiptNote().getId();
        return "—";
    }

    private <T> List<T> safe(List<T> list) {
        return list == null ? List.of() : list;
    }

    private String currentUser() {
        try {
            return SecurityContextHolder.getContext().getAuthentication().getName();
        } catch (Exception ignored) {
            return "system";
        }
    }

    private InspectionLotDto toDto(InspectionLot lot) {
        List<InspectionResultDto> results = new ArrayList<>();
        for (InspectionResult r : lot.getResults()) {
            results.add(new InspectionResultDto(
                    r.getId(), r.getParameterName(), r.getParameterType(),
                    r.getMinValue(), r.getMaxValue(), r.getUnit(), r.isCritical(),
                    r.getObservedValue(), r.getObservedText(), r.getResult(), r.getRemarks()));
        }
        return new InspectionLotDto(
                lot.getId(), lot.getLotNumber(), lot.getSource(), lot.getStatus(),
                lot.getInventoryItem().getInventoryItemId(),
                lot.getInventoryItem().getItemCode(), lot.getInventoryItem().getName(),
                lot.getWorkOrder() != null ? lot.getWorkOrder().getId() : null,
                lot.getWorkOrder() != null ? lot.getWorkOrder().getWorkOrderNumber() : null,
                lot.getWorkOrderOperation() != null ? lot.getWorkOrderOperation().getId() : null,
                lot.getGoodsReceiptNote() != null ? lot.getGoodsReceiptNote().getId() : null,
                lot.getQuantityOffered(), lot.getQuantityAccepted(), lot.getQuantityRejected(),
                lot.getInspectedBy(), lot.getInspectedDate(),
                lot.getWaivedBy(), lot.getWaiverReason(),
                lot.getRemarks(), lot.getCreatedBy(), results);
    }
}
