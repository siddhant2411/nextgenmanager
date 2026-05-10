package com.nextgenmanager.nextgenmanager.marketing.enquiry.service;

import com.nextgenmanager.nextgenmanager.Inventory.model.NumberSequence;
import com.nextgenmanager.nextgenmanager.Inventory.repository.NumberSequenceRepository;
import com.nextgenmanager.nextgenmanager.company.repository.CompanyDetailsRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

/**
 * Generates Enquiry numbers in the format ENQ/{FY}/{####}, e.g. ENQ/2026-27/0001.
 * Uses the NumberSequence table with a PESSIMISTIC_WRITE lock to prevent duplicates.
 */
@Service
public class EnquiryNumberGenerator {

    private final NumberSequenceRepository sequenceRepo;
    private final CompanyDetailsRepository companyDetailsRepository;

    public EnquiryNumberGenerator(NumberSequenceRepository sequenceRepo,
                                  CompanyDetailsRepository companyDetailsRepository) {
        this.sequenceRepo = sequenceRepo;
        this.companyDetailsRepository = companyDetailsRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public String next() {
        String fy  = currentFinancialYear();
        String key = "ENQ-" + fy;

        NumberSequence seq = sequenceRepo.findByKeyForUpdate(key)
                .orElseGet(() -> sequenceRepo.save(new NumberSequence(key, 1L)));

        long val = seq.getNextVal();
        seq.setNextVal(val + 1);
        sequenceRepo.save(seq);

        return String.format("ENQ/%s/%04d", fy, val);
    }

    /** Returns current financial year string, e.g. "2026-27". */
    public String currentFinancialYear() {
        int fyStartMonth = companyDetailsRepository.findAll().stream()
                .findFirst()
                .map(c -> c.getFinancialYearStartMonth() != null ? c.getFinancialYearStartMonth() : 4)
                .orElse(4);

        LocalDate today = LocalDate.now();
        int year = today.getMonthValue() >= fyStartMonth ? today.getYear() : today.getYear() - 1;
        return year + "-" + String.valueOf(year + 1).substring(2);
    }

    /** Preview next number without consuming the sequence. */
    public String preview() {
        String fy  = currentFinancialYear();
        String key = "ENQ-" + fy;
        long val = sequenceRepo.findById(key).map(NumberSequence::getNextVal).orElse(1L);
        return String.format("ENQ/%s/%04d", fy, val);
    }
}
