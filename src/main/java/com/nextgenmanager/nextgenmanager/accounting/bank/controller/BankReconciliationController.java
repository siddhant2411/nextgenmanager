package com.nextgenmanager.nextgenmanager.accounting.bank.controller;

import com.nextgenmanager.nextgenmanager.accounting.bank.dto.*;
import com.nextgenmanager.nextgenmanager.accounting.bank.service.BankReconciliationService;
import com.nextgenmanager.nextgenmanager.common.security.authorization.RequiresAccountingAccess;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.security.Principal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/accounting/bank")
@RequiresAccountingAccess
@RequiredArgsConstructor
public class BankReconciliationController {

    private final BankReconciliationService service;

    @GetMapping("/accounts")
    public ResponseEntity<List<BankAccountDto>> accounts() {
        return ResponseEntity.ok(service.accounts());
    }

    @GetMapping("/accounts/{ledgerId}/mapping")
    public ResponseEntity<BankStatementMappingDto> getMapping(@PathVariable Long ledgerId) {
        return ResponseEntity.ok(service.getMapping(ledgerId));
    }

    @PutMapping("/accounts/{ledgerId}/mapping")
    public ResponseEntity<BankStatementMappingDto> saveMapping(@PathVariable Long ledgerId,
                                                               @Valid @RequestBody BankStatementMappingDto dto) {
        return ResponseEntity.ok(service.saveMapping(ledgerId, dto));
    }

    @PostMapping("/statement/preview")
    public ResponseEntity<StatementPreviewDto> preview(@RequestParam("file") MultipartFile file,
                                                       @RequestParam(defaultValue = "1") int headerRow) {
        return ResponseEntity.ok(service.preview(file, headerRow));
    }

    @PostMapping("/accounts/{ledgerId}/statement")
    public ResponseEntity<StatementImportResultDto> importStatement(@PathVariable Long ledgerId,
                                                                    @RequestParam("file") MultipartFile file,
                                                                    Principal principal) {
        return ResponseEntity.ok(service.importStatement(ledgerId, file, principal.getName()));
    }

    @GetMapping("/accounts/{ledgerId}/workspace")
    public ResponseEntity<ReconciliationWorkspaceDto> workspace(
            @PathVariable Long ledgerId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOf) {
        return ResponseEntity.ok(service.workspace(ledgerId, asOf != null ? asOf : LocalDate.now()));
    }

    @PostMapping("/accounts/{ledgerId}/auto-match")
    public ResponseEntity<Map<String, Integer>> autoMatch(@PathVariable Long ledgerId, Principal principal) {
        return ResponseEntity.ok(Map.of("matched", service.autoMatch(ledgerId, principal.getName())));
    }

    @PostMapping("/match")
    public ResponseEntity<Void> match(@Valid @RequestBody MatchRequest req, Principal principal) {
        service.match(req.statementLineId(), req.voucherLineId(), principal.getName());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/statement-lines/{id}/unmatch")
    public ResponseEntity<Void> unmatch(@PathVariable Long id) {
        service.unmatch(id);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/statement-lines/{id}")
    public ResponseEntity<Void> deleteStatementLine(@PathVariable Long id) {
        service.deleteStatementLine(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/accounts/{ledgerId}/reconciliation-statement")
    public ResponseEntity<BankReconciliationStatementDto> statement(
            @PathVariable Long ledgerId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOf) {
        return ResponseEntity.ok(service.statement(ledgerId, asOf != null ? asOf : LocalDate.now()));
    }
}
