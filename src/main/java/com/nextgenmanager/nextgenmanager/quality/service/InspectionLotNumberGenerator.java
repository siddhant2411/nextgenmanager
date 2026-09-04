package com.nextgenmanager.nextgenmanager.quality.service;

import com.nextgenmanager.nextgenmanager.Inventory.model.NumberSequence;
import com.nextgenmanager.nextgenmanager.Inventory.repository.NumberSequenceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * QC/0001, QC/0002, ...
 *
 * <p>Runs in its own transaction, so a number it issues is spent even when the caller rolls back.
 * Callers must validate everything before asking for one — the pick list and the stock transfer
 * both learned that the expensive way, leaving permanent gaps in their sequences.
 */
@Service
public class InspectionLotNumberGenerator {

    private static final String KEY = "INSPECTION_LOT";

    private final NumberSequenceRepository sequenceRepo;

    public InspectionLotNumberGenerator(NumberSequenceRepository sequenceRepo) {
        this.sequenceRepo = sequenceRepo;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public String next() {
        NumberSequence seq = sequenceRepo.findByKeyForUpdate(KEY)
                .orElseGet(() -> sequenceRepo.save(new NumberSequence(KEY, 1L)));
        long val = seq.getNextVal();
        seq.setNextVal(val + 1);
        sequenceRepo.save(seq);
        return String.format("QC/%04d", val);
    }
}
