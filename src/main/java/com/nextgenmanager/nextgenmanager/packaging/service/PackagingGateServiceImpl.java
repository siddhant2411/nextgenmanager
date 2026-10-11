package com.nextgenmanager.nextgenmanager.packaging.service;

import com.nextgenmanager.nextgenmanager.items.model.InventoryItem;
import com.nextgenmanager.nextgenmanager.items.model.ProductInventorySettings;
import com.nextgenmanager.nextgenmanager.packaging.model.PackageBox;
import com.nextgenmanager.nextgenmanager.packaging.model.PackageLine;
import com.nextgenmanager.nextgenmanager.packaging.model.PackingSlip;
import com.nextgenmanager.nextgenmanager.quality.model.InspectionLot;
import com.nextgenmanager.nextgenmanager.quality.model.InspectionLotStatus;
import com.nextgenmanager.nextgenmanager.quality.repository.InspectionLotRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The quality gate on packaging.
 *
 * <p>Two rules, the same shape as the gate on production. A box whose PACKAGE lot failed, or is
 * still waiting to be judged, stops the slip with no configuration: silence is not consent. An
 * item marked {@code packageInspectionRequired} goes further and stops the slip while a box
 * holding it has no lot that cleared — off by default, so a box of anything else closes freely.
 */
@Service
@RequiredArgsConstructor
public class PackagingGateServiceImpl implements PackagingGateService {

    private static final Logger logger = LoggerFactory.getLogger(PackagingGateServiceImpl.class);

    private final InspectionLotRepository inspectionLotRepository;

    @Override
    public List<String> reasonsClosingIsBlocked(PackingSlip slip) {
        List<String> reasons = new ArrayList<>();

        for (PackageBox box : slip.getBoxes()) {
            if (box.getDeletedDate() != null) continue;

            List<InspectionLot> lots = inspectionLotRepository.findLiveByPackageBox(box.getId());
            for (InspectionLot lot : lots) {
                if (lot.getStatus() == InspectionLotStatus.FAILED) {
                    reasons.add(String.format("box %d: package inspection %s failed%s",
                            box.getBoxNumber(), lot.getLotNumber(),
                            lot.getRemarks() != null && !lot.getRemarks().isBlank()
                                    ? " (" + lot.getRemarks() + ")" : ""));
                } else if (lot.getStatus() == InspectionLotStatus.PENDING) {
                    reasons.add(String.format("box %d: package inspection %s has not been judged yet",
                            box.getBoxNumber(), lot.getLotNumber()));
                }
            }

            // An item that may not leave uninspected, in a box nobody inspected for it. A lot
            // that failed or is pending is already reported above, so only the missing one is
            // added here.
            Set<Integer> reported = new HashSet<>();
            for (PackageLine line : box.getLines()) {
                InventoryItem item = line.getInventoryItem();
                if (item == null || !requiresPackageInspection(item)) continue;
                if (!reported.add(item.getInventoryItemId())) continue;
                boolean raised = lots.stream().anyMatch(l -> sameItem(l, item));
                if (!raised) {
                    reasons.add(String.format(
                            "box %d: %s requires a package inspection and none has been raised",
                            box.getBoxNumber(), item.getItemCode()));
                }
            }
        }

        return reasons;
    }

    private boolean requiresPackageInspection(InventoryItem item) {
        ProductInventorySettings settings = item.getProductInventorySettings();
        return settings != null && settings.isPackageInspectionRequired();
    }

    private boolean sameItem(InspectionLot lot, InventoryItem item) {
        return lot.getInventoryItem() != null
                && lot.getInventoryItem().getInventoryItemId() == item.getInventoryItemId();
    }

    @Override
    public void assertClosingAllowed(PackingSlip slip) {
        List<String> reasons = reasonsClosingIsBlocked(slip);
        if (reasons.isEmpty()) return;

        logger.warn("Packaging gate stopped {}: {}", slip.getSlipNumber(), String.join("; ", reasons));
        throw new IllegalStateException(String.format(
                "%s cannot close — %s. Record a passing inspection, or waive the lot if the box is "
                        + "to ship anyway.",
                slip.getSlipNumber(), String.join("; ", reasons)));
    }
}
