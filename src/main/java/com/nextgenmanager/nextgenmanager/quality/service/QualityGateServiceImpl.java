package com.nextgenmanager.nextgenmanager.quality.service;

import com.nextgenmanager.nextgenmanager.items.model.InventoryItem;
import com.nextgenmanager.nextgenmanager.items.model.ProductInventorySettings;
import com.nextgenmanager.nextgenmanager.production.model.WorkOrder;
import com.nextgenmanager.nextgenmanager.production.model.WorkOrderLine;
import com.nextgenmanager.nextgenmanager.production.model.WorkOrderQaResult;
import com.nextgenmanager.nextgenmanager.production.repository.workorder.WorkOrderQaResultRepository;
import com.nextgenmanager.nextgenmanager.quality.model.InspectionLot;
import com.nextgenmanager.nextgenmanager.quality.model.InspectionLotStatus;
import com.nextgenmanager.nextgenmanager.quality.model.InspectionSource;
import com.nextgenmanager.nextgenmanager.quality.repository.InspectionLotRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * The quality gate on production.
 *
 * <p>Three rules, and only one of them needs configuring. A raised inspection that failed, or that
 * nobody has judged, stops the goods on its own — the lot exists precisely so that somebody
 * intended to look. Requiring an inspection that was never raised is the configured rule, off by
 * default, because switching it on everywhere at once would stop a shop floor that has never
 * raised a lot in its life.
 */
@Service
@RequiredArgsConstructor
public class QualityGateServiceImpl implements QualityGateService {

    private static final Logger logger = LoggerFactory.getLogger(QualityGateServiceImpl.class);

    private final InspectionLotRepository inspectionLotRepository;
    private final WorkOrderQaResultRepository workOrderQaResultRepository;

    @Override
    public List<String> reasonsProductionIsBlocked(WorkOrder workOrder) {
        List<String> reasons = new ArrayList<>();

        List<InspectionLot> finalLots = inspectionLotRepository
                .findLiveByWorkOrder(workOrder.getId()).stream()
                .filter(l -> l.getSource() == InspectionSource.FINAL)
                .toList();

        // 1. A final inspection that failed, or that nobody has judged yet. Silence is not
        //    consent: somebody raised this lot because these goods were to be looked at.
        for (InspectionLot lot : finalLots) {
            if (lot.getStatus() == InspectionLotStatus.FAILED) {
                reasons.add(String.format("final inspection %s failed%s",
                        lot.getLotNumber(),
                        lot.getRemarks() != null && !lot.getRemarks().isBlank()
                                ? " (" + lot.getRemarks() + ")" : ""));
            } else if (lot.getStatus() == InspectionLotStatus.PENDING) {
                reasons.add(String.format("final inspection %s has not been judged yet",
                        lot.getLotNumber()));
            }
        }

        // 2. An item whose finished goods are not allowed out without an inspection, and no lot
        //    has cleared. Per item, and off unless somebody turned it on.
        for (InventoryItem produced : producedItems(workOrder)) {
            if (!requiresFinalInspection(produced)) continue;
            boolean cleared = finalLots.stream()
                    .anyMatch(l -> sameItem(l, produced) && l.clearsTheGate());
            if (!cleared) {
                reasons.add(String.format(
                        "%s requires a final inspection and none has passed",
                        produced.getItemCode()));
            }
        }

        // 3. A critical parameter that was measured and failed. This is the hole the phase was
        //    raised to close: these results have always been recorded and never acted on.
        List<WorkOrderQaResult> failures =
                workOrderQaResultRepository.findFailedCriticalForWorkOrder(workOrder.getId());
        for (WorkOrderQaResult failure : failures) {
            String parameter = failure.getWorkOrderQaEntry() != null
                    ? failure.getWorkOrderQaEntry().getParameterName() : "a critical check";
            reasons.add(String.format("critical check '%s' failed%s", parameter,
                    failure.getRemarks() != null && !failure.getRemarks().isBlank()
                            ? " (" + failure.getRemarks() + ")" : ""));
        }

        return reasons;
    }

    @Override
    public void assertProductionAllowed(WorkOrder workOrder) {
        List<String> reasons = reasonsProductionIsBlocked(workOrder);
        if (reasons.isEmpty()) return;

        logger.warn("Quality gate stopped WorkOrder {}: {}",
                workOrder.getWorkOrderNumber(), String.join("; ", reasons));
        throw new IllegalStateException(String.format(
                "WorkOrder %s cannot produce finished goods — %s. Record a passing inspection, or "
                        + "waive the lot if the goods are to be released anyway.",
                workOrder.getWorkOrderNumber(), String.join("; ", reasons)));
    }

    // ─── helpers ──────────────────────────────────────────────────────────────

    /** What this order makes: one item per active line. */
    private List<InventoryItem> producedItems(WorkOrder workOrder) {
        List<InventoryItem> items = new ArrayList<>();
        for (WorkOrderLine line : workOrder.activeLines()) {
            if (line.getInventoryItem() != null) items.add(line.getInventoryItem());
        }
        return items;
    }

    private boolean requiresFinalInspection(InventoryItem item) {
        ProductInventorySettings settings = item.getProductInventorySettings();
        return settings != null && settings.isFinalInspectionRequired();
    }

    private boolean sameItem(InspectionLot lot, InventoryItem item) {
        return lot.getInventoryItem() != null
                && lot.getInventoryItem().getInventoryItemId() == item.getInventoryItemId();
    }
}
