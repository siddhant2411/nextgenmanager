package com.nextgenmanager.nextgenmanager.accounting.period.controller;

import com.nextgenmanager.nextgenmanager.accounting.close.dto.ReopenYearRequest;
import com.nextgenmanager.nextgenmanager.accounting.close.dto.YearEndClosePreviewDto;
import com.nextgenmanager.nextgenmanager.accounting.close.service.YearEndCloseService;
import com.nextgenmanager.nextgenmanager.accounting.period.dto.AccountingPeriodDto;
import com.nextgenmanager.nextgenmanager.accounting.period.dto.FinancialYearCreateDto;
import com.nextgenmanager.nextgenmanager.accounting.period.dto.FinancialYearDto;
import com.nextgenmanager.nextgenmanager.accounting.period.service.FinancialYearService;
import com.nextgenmanager.nextgenmanager.common.security.authorization.RequiresAccountingAccess;
import com.nextgenmanager.nextgenmanager.common.security.authorization.RequiresAccountsHead;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;

@RestController
@RequestMapping("/api/accounting/financial-years")
@RequiresAccountingAccess
@RequiredArgsConstructor
public class FinancialYearController {

    private final FinancialYearService fyService;
    private final YearEndCloseService yearEndCloseService;

    @PostMapping
    public ResponseEntity<FinancialYearDto> create(@Valid @RequestBody FinancialYearCreateDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(fyService.create(dto));
    }

    @GetMapping
    public ResponseEntity<List<FinancialYearDto>> listAll() {
        return ResponseEntity.ok(fyService.listAll());
    }

    @GetMapping("/{id}")
    public ResponseEntity<FinancialYearDto> getById(@PathVariable Long id) {
        return ResponseEntity.ok(fyService.getById(id));
    }

    @PatchMapping("/periods/{periodId}/lock")
    public ResponseEntity<AccountingPeriodDto> lockPeriod(@PathVariable Long periodId) {
        return ResponseEntity.ok(fyService.lockPeriod(periodId));
    }

    @PatchMapping("/periods/{periodId}/unlock")
    public ResponseEntity<AccountingPeriodDto> unlockPeriod(@PathVariable Long periodId) {
        return ResponseEntity.ok(fyService.unlockPeriod(periodId));
    }

    // ─── Year-end close (Phase 5) ────────────────────────────────────────────

    @GetMapping("/{id}/close-preview")
    public ResponseEntity<YearEndClosePreviewDto> closePreview(@PathVariable Long id) {
        return ResponseEntity.ok(yearEndCloseService.preview(id));
    }

    @PostMapping("/{id}/close")
    @RequiresAccountsHead
    public ResponseEntity<FinancialYearDto> close(@PathVariable Long id, Principal principal) {
        return ResponseEntity.ok(yearEndCloseService.close(id, principal.getName()));
    }

    @PostMapping("/{id}/reopen")
    @RequiresAccountsHead
    public ResponseEntity<FinancialYearDto> reopen(@PathVariable Long id, @Valid @RequestBody ReopenYearRequest req,
                                                   Principal principal) {
        return ResponseEntity.ok(yearEndCloseService.reopen(id, req.getReason(), principal.getName()));
    }
}
