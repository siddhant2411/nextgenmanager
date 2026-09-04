package com.nextgenmanager.nextgenmanager.Inventory.service;

import com.nextgenmanager.nextgenmanager.Inventory.model.NumberSequence;
import com.nextgenmanager.nextgenmanager.Inventory.repository.NumberSequenceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * PK/0001, PK/0002, ...
 *
 * <p>Runs in its own transaction, so a number it issues is spent even if the caller rolls back.
 * Callers must therefore validate everything before asking for one — see the same lesson learned
 * in {@code StockTransferServiceImpl.create}.
 */
@Service
public class PickListNumberGenerator {

    private static final String KEY = "PICK_LIST";

    private final NumberSequenceRepository sequenceRepo;

    public PickListNumberGenerator(NumberSequenceRepository sequenceRepo) {
        this.sequenceRepo = sequenceRepo;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public String next() {
        NumberSequence seq = sequenceRepo.findByKeyForUpdate(KEY)
                .orElseGet(() -> sequenceRepo.save(new NumberSequence(KEY, 1L)));
        long val = seq.getNextVal();
        seq.setNextVal(val + 1);
        sequenceRepo.save(seq);
        return String.format("PK/%04d", val);
    }
}
