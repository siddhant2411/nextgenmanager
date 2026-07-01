package com.nextgenmanager.nextgenmanager.accounting.tds.service;

import com.nextgenmanager.nextgenmanager.accounting.tds.dto.TdsChallanCreateDto;
import com.nextgenmanager.nextgenmanager.accounting.tds.dto.TdsChallanDto;

import java.util.List;

public interface TdsChallanService {

    /** Records a challan for the pending (DEDUCTED) entries of a FY+quarter, flips them to DEPOSITED. */
    TdsChallanDto createChallan(TdsChallanCreateDto dto, String username);

    List<TdsChallanDto> listChallans(String financialYear);

    TdsChallanDto getChallan(Long id);
}
