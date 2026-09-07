package com.nextgenmanager.nextgenmanager.quality.service;

import com.nextgenmanager.nextgenmanager.Inventory.model.NumberSequence;
import com.nextgenmanager.nextgenmanager.Inventory.repository.NumberSequenceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * NCR/0001, NCR/0002, ...
 *
 * <p>Its own transaction, so a number it hands out is spent even when the caller rolls back:
 * validate before asking for one.
 */
@Service
public class NcrNumberGenerator {

    private static final String KEY = "NCR";

    private final NumberSequenceRepository sequenceRepo;

    public NcrNumberGenerator(NumberSequenceRepository sequenceRepo) {
        this.sequenceRepo = sequenceRepo;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public String next() {
        NumberSequence seq = sequenceRepo.findByKeyForUpdate(KEY)
                .orElseGet(() -> sequenceRepo.save(new NumberSequence(KEY, 1L)));
        long val = seq.getNextVal();
        seq.setNextVal(val + 1);
        sequenceRepo.save(seq);
        return String.format("NCR/%04d", val);
    }
}
