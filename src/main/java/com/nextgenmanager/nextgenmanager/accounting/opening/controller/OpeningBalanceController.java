package com.nextgenmanager.nextgenmanager.accounting.opening.controller;

import com.nextgenmanager.nextgenmanager.accounting.opening.service.OpeningBalanceService;
import com.nextgenmanager.nextgenmanager.accounting.voucher.dto.VoucherDto;
import com.nextgenmanager.nextgenmanager.common.security.authorization.RequiresAccountingAccess;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.security.Principal;
import java.time.LocalDate;

@RestController
@RequestMapping("/api/accounting/opening-balance")
@RequiresAccountingAccess
@RequiredArgsConstructor
public class OpeningBalanceController {

    private final OpeningBalanceService openingBalanceService;

    /**
     * Upload an Excel file with opening balances.
     * Expected columns: A=ledgerCode, B=contactCode(optional), C=amount, D=DR|CR
     */
    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN','ACCOUNTS_ADMIN')")
    public ResponseEntity<VoucherDto> importOpeningBalances(
            @RequestParam("file") MultipartFile file,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate openingDate,
            Principal principal) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(openingBalanceService.importFromExcel(file, openingDate, principal.getName()));
    }

    @PatchMapping("/financial-years/{fyId}/opening-date")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN','ACCOUNTS_ADMIN')")
    public ResponseEntity<Void> setOpeningDate(
            @PathVariable Long fyId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate openingDate) {
        openingBalanceService.setOpeningDate(fyId, openingDate);
        return ResponseEntity.noContent().build();
    }
}
