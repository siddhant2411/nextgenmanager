package com.nextgenmanager.nextgenmanager.Inventory.service;

import com.nextgenmanager.nextgenmanager.Inventory.model.NumberSequence;
import com.nextgenmanager.nextgenmanager.Inventory.repository.NumberSequenceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** ST/0001, ST/0002, ... Follows the lazy-sequence convention used by every other generator. */
@Service
public class StockTransferNumberGenerator {

    private static final String KEY = "STOCK_TRANSFER";

    private final NumberSequenceRepository sequenceRepo;

    public StockTransferNumberGenerator(NumberSequenceRepository sequenceRepo) {
        this.sequenceRepo = sequenceRepo;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public String next() {
        NumberSequence seq = sequenceRepo.findByKeyForUpdate(KEY)
                .orElseGet(() -> sequenceRepo.save(new NumberSequence(KEY, 1L)));
        long val = seq.getNextVal();
        seq.setNextVal(val + 1);
        sequenceRepo.save(seq);
        return String.format("ST/%04d", val);
    }
}
