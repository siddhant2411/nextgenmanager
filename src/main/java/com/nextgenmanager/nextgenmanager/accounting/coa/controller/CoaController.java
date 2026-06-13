package com.nextgenmanager.nextgenmanager.accounting.coa.controller;

import com.nextgenmanager.nextgenmanager.accounting.coa.dto.*;
import com.nextgenmanager.nextgenmanager.accounting.coa.service.CoaService;
import com.nextgenmanager.nextgenmanager.common.security.authorization.RequiresAccountingAccess;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/accounting")
@RequiresAccountingAccess
@RequiredArgsConstructor
public class CoaController {

    private final CoaService coaService;

    // ─── COA Tree ──────────────────────────────────────────────────────────────

    @GetMapping("/coa/tree")
    public ResponseEntity<List<CoaTreeNodeDto>> getTree() {
        return ResponseEntity.ok(coaService.getTree());
    }

    // ─── Account Groups ────────────────────────────────────────────────────────

    @GetMapping("/account-groups")
    public ResponseEntity<List<AccountGroupDto>> listGroups() {
        return ResponseEntity.ok(coaService.listGroups());
    }

    @PostMapping("/account-groups")
    public ResponseEntity<AccountGroupDto> createGroup(@Valid @RequestBody AccountGroupCreateDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(coaService.createGroup(dto));
    }

    @PutMapping("/account-groups/{id}")
    public ResponseEntity<AccountGroupDto> updateGroup(@PathVariable Long id,
                                                        @Valid @RequestBody AccountGroupCreateDto dto) {
        return ResponseEntity.ok(coaService.updateGroup(id, dto));
    }

    @DeleteMapping("/account-groups/{id}")
    public ResponseEntity<Void> deleteGroup(@PathVariable Long id) {
        coaService.deleteGroup(id);
        return ResponseEntity.noContent().build();
    }

    // ─── Ledger Accounts ───────────────────────────────────────────────────────

    @GetMapping("/ledger-accounts")
    public ResponseEntity<List<LedgerAccountDto>> listLedgers() {
        return ResponseEntity.ok(coaService.listLedgers());
    }

    @GetMapping("/ledger-accounts/search")
    public ResponseEntity<List<LedgerAccountDto>> searchLedgers(@RequestParam String q) {
        return ResponseEntity.ok(coaService.searchLedgers(q));
    }

    @GetMapping("/ledger-accounts/{id}")
    public ResponseEntity<LedgerAccountDto> getLedger(@PathVariable Long id) {
        return ResponseEntity.ok(coaService.getLedgerById(id));
    }

    @PostMapping("/ledger-accounts")
    public ResponseEntity<LedgerAccountDto> createLedger(@Valid @RequestBody LedgerAccountCreateDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(coaService.createLedger(dto));
    }

    @PutMapping("/ledger-accounts/{id}")
    public ResponseEntity<LedgerAccountDto> updateLedger(@PathVariable Long id,
                                                           @Valid @RequestBody LedgerAccountCreateDto dto) {
        return ResponseEntity.ok(coaService.updateLedger(id, dto));
    }

    @DeleteMapping("/ledger-accounts/{id}")
    public ResponseEntity<Void> deleteLedger(@PathVariable Long id) {
        coaService.deleteLedger(id);
        return ResponseEntity.noContent().build();
    }

    // ─── Contact ↔ Ledger ─────────────────────────────────────────────────────

    @GetMapping("/contacts/{contactId}/ledgers")
    public ResponseEntity<List<LedgerAccountDto>> getContactLedgers(@PathVariable int contactId) {
        return ResponseEntity.ok(coaService.getContactLedgers(contactId));
    }

    @PostMapping("/contacts/{contactId}/ensure-ledger")
    public ResponseEntity<List<LedgerAccountDto>> ensureContactLedger(@PathVariable int contactId) {
        return ResponseEntity.ok(coaService.ensureContactLedgers(contactId));
    }
}
