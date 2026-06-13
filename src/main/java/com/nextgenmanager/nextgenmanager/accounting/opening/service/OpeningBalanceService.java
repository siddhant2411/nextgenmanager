package com.nextgenmanager.nextgenmanager.accounting.opening.service;

import com.nextgenmanager.nextgenmanager.accounting.voucher.dto.VoucherDto;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.List;

public interface OpeningBalanceService {
    /** Import from Excel; posts one OPENING voucher on success. Returns the voucher. */
    VoucherDto importFromExcel(MultipartFile file, LocalDate openingDate, String username);

    /** Mark the financial year's openingDate (call before import). */
    void setOpeningDate(Long financialYearId, LocalDate openingDate);
}
