package com.nextgenmanager.nextgenmanager.accounting.reports.controller;

import com.nextgenmanager.nextgenmanager.accounting.reports.dto.AgeingReportDto;
import com.nextgenmanager.nextgenmanager.accounting.reports.dto.DayBookDto;
import com.nextgenmanager.nextgenmanager.accounting.reports.dto.LedgerStatementDto;
import com.nextgenmanager.nextgenmanager.accounting.reports.dto.StockGlReconciliationDto;
import com.nextgenmanager.nextgenmanager.accounting.reports.dto.TrialBalanceDto;
import com.nextgenmanager.nextgenmanager.accounting.reports.service.AccountingReportService;
import com.nextgenmanager.nextgenmanager.accounting.reports.service.AgeingReportService;
import com.nextgenmanager.nextgenmanager.accounting.reports.service.StockGlReconciliationService;
import com.nextgenmanager.nextgenmanager.common.security.authorization.RequiresAccountingAccess;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/accounting/reports")
@RequiresAccountingAccess
@RequiredArgsConstructor
public class AccountingReportController {

    private static final String XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    private final AccountingReportService reportService;
    private final AgeingReportService ageingReportService;
    private final StockGlReconciliationService stockGlReconciliationService;

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

    // ─── Ageing (1E) ─────────────────────────────────────────────────────────

    @GetMapping("/debtors-ageing")
    public ResponseEntity<AgeingReportDto> debtorsAgeing(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOf) {
        return ResponseEntity.ok(ageingReportService.debtorsAgeing(asOf != null ? asOf : LocalDate.now()));
    }

    @GetMapping("/creditors-ageing")
    public ResponseEntity<AgeingReportDto> creditorsAgeing(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOf) {
        return ResponseEntity.ok(ageingReportService.creditorsAgeing(asOf != null ? asOf : LocalDate.now()));
    }

    @GetMapping("/debtors-ageing/excel")
    public ResponseEntity<byte[]> debtorsAgeingExcel(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOf) {
        LocalDate d = asOf != null ? asOf : LocalDate.now();
        byte[] body = ageingReportService.toExcel(ageingReportService.debtorsAgeing(d));
        return excel(body, "Debtors_Ageing_" + d + ".xlsx");
    }

    @GetMapping("/creditors-ageing/excel")
    public ResponseEntity<byte[]> creditorsAgeingExcel(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOf) {
        LocalDate d = asOf != null ? asOf : LocalDate.now();
        byte[] body = ageingReportService.toExcel(ageingReportService.creditorsAgeing(d));
        return excel(body, "Creditors_Ageing_" + d + ".xlsx");
    }

    // ─── Stock vs GL reconciliation (3.4 — perpetual inventory) ───────────────

    @GetMapping("/stock-gl-reconciliation")
    public ResponseEntity<StockGlReconciliationDto> stockGlReconciliation(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOf) {
        return ResponseEntity.ok(stockGlReconciliationService.reconcile(from, asOf != null ? asOf : LocalDate.now()));
    }

    @GetMapping("/stock-gl-reconciliation/excel")
    public ResponseEntity<byte[]> stockGlReconciliationExcel(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOf) {
        LocalDate d = asOf != null ? asOf : LocalDate.now();
        byte[] body = stockGlReconciliationService.toExcel(stockGlReconciliationService.reconcile(from, d));
        return excel(body, "Stock_GL_Reconciliation_" + d + ".xlsx");
    }

    private ResponseEntity<byte[]> excel(byte[] body, String fileName) {
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(XLSX))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + "\"")
                .body(body);
    }
}
