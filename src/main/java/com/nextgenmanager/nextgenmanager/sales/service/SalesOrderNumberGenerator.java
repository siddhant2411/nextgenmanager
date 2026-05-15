package com.nextgenmanager.nextgenmanager.sales.service;

import com.nextgenmanager.nextgenmanager.Inventory.model.NumberSequence;
import com.nextgenmanager.nextgenmanager.Inventory.repository.NumberSequenceRepository;
import com.nextgenmanager.nextgenmanager.company.repository.CompanyDetailsRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

@Service
public class SalesOrderNumberGenerator {

    private final NumberSequenceRepository sequenceRepo;
    private final CompanyDetailsRepository companyDetailsRepository;

    public SalesOrderNumberGenerator(NumberSequenceRepository sequenceRepo,
                                     CompanyDetailsRepository companyDetailsRepository) {
        this.sequenceRepo = sequenceRepo;
        this.companyDetailsRepository = companyDetailsRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public String next() {
        String fy  = currentFinancialYear();
        String key = "SO-" + fy;

        NumberSequence seq = sequenceRepo.findByKeyForUpdate(key)
                .orElseGet(() -> sequenceRepo.save(new NumberSequence(key, 1L)));

        long val = seq.getNextVal();
        seq.setNextVal(val + 1);
        sequenceRepo.save(seq);

        return String.format("SO/%s/%04d", fy, val);
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
        String key = "SO-" + fy;
        long val = sequenceRepo.findById(key).map(NumberSequence::getNextVal).orElse(1L);
        return String.format("SO/%s/%04d", fy, val);
    }
}
