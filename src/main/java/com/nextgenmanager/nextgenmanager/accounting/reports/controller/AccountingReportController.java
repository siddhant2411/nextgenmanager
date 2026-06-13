package com.nextgenmanager.nextgenmanager.accounting.reports.controller;

import com.nextgenmanager.nextgenmanager.accounting.reports.dto.DayBookDto;
import com.nextgenmanager.nextgenmanager.accounting.reports.dto.LedgerStatementDto;
import com.nextgenmanager.nextgenmanager.accounting.reports.dto.TrialBalanceDto;
import com.nextgenmanager.nextgenmanager.accounting.reports.service.AccountingReportService;
import com.nextgenmanager.nextgenmanager.common.security.authorization.RequiresAccountingAccess;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/accounting/reports")
@RequiresAccountingAccess
@RequiredArgsConstructor
public class AccountingReportController {

    private final AccountingReportService reportService;

    @GetMapping("/trial-balance")
    public ResponseEntity<TrialBalanceDto> trialBalance(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOf) {
        return ResponseEntity.ok(reportService.trialBalance(asOf != null ? asOf : LocalDate.now()));
    }

    @GetMapping("/day-book")
    public ResponseEntity<DayBookDto> dayBook(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(reportService.dayBook(from, to));
    }

    @GetMapping("/ledger/{accountId}")
    public ResponseEntity<LedgerStatementDto> ledgerStatement(
            @PathVariable Long accountId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(reportService.ledgerStatement(accountId, from, to));
    }
}
