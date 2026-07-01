package com.nextgenmanager.nextgenmanager.accounting.tds.service;

import com.nextgenmanager.nextgenmanager.accounting.tds.dto.TdsSectionCreateDto;
import com.nextgenmanager.nextgenmanager.accounting.tds.dto.TdsSectionDto;

import java.util.List;

public interface TdsSectionService {

    /** All non-deleted sections (admin view). */
    List<TdsSectionDto> listAll();

    /** Active sections only (for the payment dropdown). */
    List<TdsSectionDto> listActive();

    TdsSectionDto create(TdsSectionCreateDto dto);

    TdsSectionDto update(Long id, TdsSectionCreateDto dto);

    void delete(Long id);
}
