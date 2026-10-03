package com.nextgenmanager.nextgenmanager.packaging.service;

import com.nextgenmanager.nextgenmanager.Inventory.model.NumberSequence;
import com.nextgenmanager.nextgenmanager.Inventory.repository.NumberSequenceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * PS/0001, PS/0002, ...
 *
 * <p>Runs in its own transaction, so a number it issues is spent even if the caller rolls back —
 * the same lesson {@code PickListNumberGenerator} and {@code InspectionLotNumberGenerator} apply.
 */
@Service
public class PackingSlipNumberGenerator {

    private static final String KEY = "PACKING_SLIP";

    private final NumberSequenceRepository sequenceRepo;

    public PackingSlipNumberGenerator(NumberSequenceRepository sequenceRepo) {
        this.sequenceRepo = sequenceRepo;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public String next() {
        NumberSequence seq = sequenceRepo.findByKeyForUpdate(KEY)
                .orElseGet(() -> sequenceRepo.save(new NumberSequence(KEY, 1L)));
        long val = seq.getNextVal();
        seq.setNextVal(val + 1);
        sequenceRepo.save(seq);
        return String.format("PS/%04d", val);
    }
}
