package com.nextgenmanager.nextgenmanager.purchase.requisition.service;

import com.nextgenmanager.nextgenmanager.Inventory.model.NumberSequence;
import com.nextgenmanager.nextgenmanager.Inventory.repository.NumberSequenceRepository;
import com.nextgenmanager.nextgenmanager.company.repository.CompanyDetailsRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

/** Generates PR numbers in the format PR/{FY}/{####}, e.g. PR/2025-26/0001. */
@Service
public class PurchaseRequisitionNumberGenerator {

    private final NumberSequenceRepository sequenceRepo;
    private final CompanyDetailsRepository companyDetailsRepository;

    public PurchaseRequisitionNumberGenerator(NumberSequenceRepository sequenceRepo,
                                              CompanyDetailsRepository companyDetailsRepository) {
        this.sequenceRepo = sequenceRepo;
        this.companyDetailsRepository = companyDetailsRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public String next() {
        String fy  = currentFinancialYear();
        String key = "PR-" + fy;

        NumberSequence seq = sequenceRepo.findByKeyForUpdate(key)
                .orElseGet(() -> sequenceRepo.save(new NumberSequence(key, 1L)));

        long val = seq.getNextVal();
        seq.setNextVal(val + 1);
        sequenceRepo.save(seq);

        return String.format("PR/%s/%04d", fy, val);
    }

    public String currentFinancialYear() {
        int fyStartMonth = companyDetailsRepository.findAll().stream()
                .findFirst()
                .map(c -> c.getFinancialYearStartMonth() != null ? c.getFinancialYearStartMonth() : 4)
                .orElse(4);

        LocalDate today = LocalDate.now();
        int year = today.getMonthValue() >= fyStartMonth ? today.getYear() : today.getYear() - 1;
        return year + "-" + String.valueOf(year + 1).substring(2);
    }

    public String preview() {
        String fy  = currentFinancialYear();
        String key = "PR-" + fy;
        long val = sequenceRepo.findById(key).map(NumberSequence::getNextVal).orElse(1L);
        return String.format("PR/%s/%04d", fy, val);
    }
}
