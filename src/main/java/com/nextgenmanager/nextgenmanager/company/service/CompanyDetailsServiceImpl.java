package com.nextgenmanager.nextgenmanager.company.service;

import com.nextgenmanager.nextgenmanager.company.dto.CompanyDetailsDTO;
import com.nextgenmanager.nextgenmanager.company.dto.CompanyDetailsRequestDTO;
import com.nextgenmanager.nextgenmanager.company.model.CompanyDetails;
import com.nextgenmanager.nextgenmanager.company.repository.CompanyDetailsRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class CompanyDetailsServiceImpl implements CompanyDetailsService {

    @Autowired
    private CompanyDetailsRepository repository;

    @Override
    public CompanyDetailsDTO get() {
        List<CompanyDetails> all = repository.findAll();
        if (all.isEmpty()) {
            return new CompanyDetailsDTO(null, "", null, null, null, null, null, null,
                    null, null, null, null, null, null, null, null, "India", "INR", 4, null,
                    null, null, null, null, null, null);
        }
        return toDTO(all.get(0));
    }

    @Override
    public CompanyDetailsDTO upsert(CompanyDetailsRequestDTO request) {
        List<CompanyDetails> all = repository.findAll();
        CompanyDetails entity = all.isEmpty() ? new CompanyDetails() : all.get(0);

        entity.setCompanyName(request.companyName());
        entity.setLegalName(request.legalName());
        entity.setTradeName(request.tradeName());
        entity.setGstNumber(request.gstNumber());
        entity.setPanNumber(request.panNumber());
        entity.setTan(request.tan());
        entity.setCinNumber(request.cinNumber());
        entity.setPhone(request.phone());
        entity.setEmail(request.email());
        entity.setWebsite(request.website());
        entity.setStreet1(request.street1());
        entity.setStreet2(request.street2());
        entity.setCity(request.city());
        entity.setState(request.state());
        entity.setPinCode(request.pinCode());
        entity.setCountry(request.country() != null ? request.country() : "India");
        entity.setCurrency(request.currency() != null ? request.currency() : "INR");
        entity.setFinancialYearStartMonth(request.financialYearStartMonth() != null ? request.financialYearStartMonth() : 4);
        entity.setNotes(request.notes());
        entity.setBankName(request.bankName());
        entity.setBankAccountNumber(request.bankAccountNumber());
        entity.setBankIfscCode(request.bankIfscCode());
        entity.setBankBranch(request.bankBranch());

        return toDTO(repository.save(entity));
    }

    private CompanyDetailsDTO toDTO(CompanyDetails e) {
        return new CompanyDetailsDTO(
                e.getId(),
                e.getCompanyName(),
                e.getLegalName(),
                e.getTradeName(),
                e.getGstNumber(),
                e.getPanNumber(),
                e.getTan(),
                e.getCinNumber(),
                e.getPhone(),
                e.getEmail(),
                e.getWebsite(),
                e.getStreet1(),
                e.getStreet2(),
                e.getCity(),
                e.getState(),
                e.getPinCode(),
                e.getCountry(),
                e.getCurrency(),
                e.getFinancialYearStartMonth(),
                e.getNotes(),
                e.getBankName(),
                e.getBankAccountNumber(),
                e.getBankIfscCode(),
                e.getBankBranch(),
                e.getCreationDate(),
                e.getUpdatedDate()
        );
    }
}
