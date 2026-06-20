package com.nextgenmanager.nextgenmanager.accounting.voucher.controller;

import com.nextgenmanager.nextgenmanager.accounting.voucher.dto.DepreciationVoucherRequest;
import com.nextgenmanager.nextgenmanager.accounting.voucher.dto.PayrollVoucherRequest;
import com.nextgenmanager.nextgenmanager.accounting.voucher.dto.VoucherDto;
import com.nextgenmanager.nextgenmanager.accounting.voucher.service.StatutoryVoucherService;
import com.nextgenmanager.nextgenmanager.common.security.authorization.RequiresAccountsHead;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;

/**
 * Structured statutory journal vouchers (Phase 4 — payroll & depreciation). Restricted to the
 * accounts head; they post straight to POSTED (sourceDocType set bypasses the manual-voucher gate).
 */
@RestController
@RequestMapping("/api/accounting/vouchers")
@RequiresAccountsHead
@RequiredArgsConstructor
public class StatutoryVoucherController {

    private final StatutoryVoucherService statutoryVoucherService;

    @PostMapping("/payroll")
    public ResponseEntity<VoucherDto> postPayroll(@Valid @RequestBody PayrollVoucherRequest req, Principal principal) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(statutoryVoucherService.postPayroll(req, principal.getName()));
    }

    @PostMapping("/depreciation")
    public ResponseEntity<VoucherDto> postDepreciation(@Valid @RequestBody DepreciationVoucherRequest req, Principal principal) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(statutoryVoucherService.postDepreciation(req, principal.getName()));
    }
}
