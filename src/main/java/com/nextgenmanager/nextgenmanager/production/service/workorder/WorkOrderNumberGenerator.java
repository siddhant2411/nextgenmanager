package com.nextgenmanager.nextgenmanager.production.service.workorder;

import com.nextgenmanager.nextgenmanager.Inventory.model.NumberSequence;
import com.nextgenmanager.nextgenmanager.Inventory.repository.NumberSequenceRepository;
import com.nextgenmanager.nextgenmanager.company.repository.CompanyDetailsRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

/**
 * Generates work order numbers in the format WO/{FY}/{####}, e.g. WO/2025-26/0001 —
 * the same scheme used by {@code SalesOrderNumberGenerator} and
 * {@code PurchaseOrderNumberGenerator}.
 * Uses the existing NumberSequence table with a PESSIMISTIC_WRITE lock to prevent duplicates.
 * Financial year is derived from CompanyDetails.financialYearStartMonth (default 4 = April).
 */
@Service
public class WorkOrderNumberGenerator {

    private final NumberSequenceRepository sequenceRepo;
    private final CompanyDetailsRepository companyDetailsRepository;

    public WorkOrderNumberGenerator(NumberSequenceRepository sequenceRepo,
                                    CompanyDetailsRepository companyDetailsRepository) {
        this.sequenceRepo = sequenceRepo;
        this.companyDetailsRepository = companyDetailsRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public String next() {
        String fy  = currentFinancialYear();
        String key = "WO-" + fy;

        NumberSequence seq = sequenceRepo.findByKeyForUpdate(key)
                .orElseGet(() -> sequenceRepo.save(new NumberSequence(key, 1L)));

        long val = seq.getNextVal();
        seq.setNextVal(val + 1);
        sequenceRepo.save(seq);

        return String.format("WO/%s/%04d", fy, val);
    }

    /** Returns current financial year string, e.g. "2025-26". */
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
        String key = "WO-" + fy;
        long val = sequenceRepo.findById(key).map(NumberSequence::getNextVal).orElse(1L);
        return String.format("WO/%s/%04d", fy, val);
    }
}
