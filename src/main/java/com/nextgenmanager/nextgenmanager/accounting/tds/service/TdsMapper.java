package com.nextgenmanager.nextgenmanager.accounting.tds.service;

import com.nextgenmanager.nextgenmanager.accounting.tds.dto.TdsEntryDto;
import com.nextgenmanager.nextgenmanager.accounting.tds.model.TdsEntry;
import com.nextgenmanager.nextgenmanager.contact.model.Contact;

/** Maps TdsEntry → TdsEntryDto (shared by the register, challan, and 26Q export). */
public final class TdsMapper {

    private TdsMapper() {}

    public static TdsEntryDto toEntryDto(TdsEntry e) {
        TdsEntryDto dto = new TdsEntryDto();
        dto.setId(e.getId());
        if (e.getSection() != null) {
            dto.setSection(e.getSection().getSection());
            dto.setSectionDescription(e.getSection().getDescription());
        }
        Contact c = e.getContact();
        if (c != null) {
            dto.setDeducteeName(c.getCompanyName());
            dto.setDeducteePan(c.getPanNumber());
        }
        dto.setTaxableAmount(e.getTaxableAmount());
        dto.setTdsAmount(e.getTdsAmount());
        dto.setRate(e.getRate());
        dto.setDeductionDate(e.getDeductionDate());
        dto.setFinancialYear(e.getFinancialYear());
        dto.setQuarter(e.getQuarter());
        dto.setStatus(e.getStatus() != null ? e.getStatus().name() : null);
        dto.setChallanId(e.getChallanId());
        return dto;
    }
}
