package com.nextgenmanager.nextgenmanager.packaging.service;

import com.nextgenmanager.nextgenmanager.packaging.model.PackageBox;
import com.nextgenmanager.nextgenmanager.packaging.model.PackingSlip;
import com.nextgenmanager.nextgenmanager.quality.model.InspectionLot;
import com.nextgenmanager.nextgenmanager.quality.model.InspectionLotStatus;
import com.nextgenmanager.nextgenmanager.quality.repository.InspectionLotRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * The quality gate on packaging.
 *
 * <p>There is no per-item "package inspection required" toggle the way phase H has one for
 * finished goods — nobody has packed a box through this system before, so there is no existing
 * habit to preserve by defaulting it off. A box with no PACKAGE lot closes freely; a box whose lot
 * failed, or is still waiting to be judged, stops the slip. Silence is not consent here either.
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
        }

        return reasons;
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
