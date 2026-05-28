package com.nextgenmanager.nextgenmanager.purchase.controller;

import com.nextgenmanager.nextgenmanager.purchase.dto.CreateDebitNoteRequest;
import com.nextgenmanager.nextgenmanager.purchase.dto.DebitNoteListDTO;
import com.nextgenmanager.nextgenmanager.purchase.dto.DebitNoteResponseDTO;
import com.nextgenmanager.nextgenmanager.purchase.service.DebitNoteService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import com.nextgenmanager.nextgenmanager.common.security.authorization.*;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/debit-notes")
@RequiresPurchaseAccess
public class DebitNoteController {

    private final DebitNoteService service;

    public DebitNoteController(DebitNoteService service) {
        this.service = service;
    }

    // ── Create ────────────────────────────────────────────────────────────────

    @PostMapping
    @RequiresPurchaseInventoryAdminAccess
    public ResponseEntity<DebitNoteResponseDTO> create(@RequestBody CreateDebitNoteRequest request) {
        return ResponseEntity.ok(service.create(request));
    }

    // ── Read ──────────────────────────────────────────────────────────────────

    @GetMapping("/{id}")
    public ResponseEntity<DebitNoteResponseDTO> getById(@PathVariable Long id) {
        return ResponseEntity.ok(service.getById(id));
    }

    @GetMapping
    public ResponseEntity<Page<DebitNoteListDTO>> getAll(
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size) {
        PageRequest pr = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdDate"));
        return ResponseEntity.ok(service.getAll(pr));
    }

    @GetMapping("/by-vendor")
    public ResponseEntity<List<DebitNoteListDTO>> getByVendor(@RequestParam int vendorId) {
        return ResponseEntity.ok(service.getByVendor(vendorId));
    }

    @GetMapping("/by-grn")
    public ResponseEntity<List<DebitNoteListDTO>> getByGrn(@RequestParam Long grnId) {
        return ResponseEntity.ok(service.getByGrn(grnId));
    }

    @GetMapping("/by-po")
    public ResponseEntity<List<DebitNoteListDTO>> getByPo(@RequestParam Long poId) {
        return ResponseEntity.ok(service.getByPurchaseOrder(poId));
    }

    @GetMapping("/next-number")
    public ResponseEntity<String> nextNumber() {
        return ResponseEntity.ok(service.nextNumber());
    }

    // ── State transitions ─────────────────────────────────────────────────────

    @PutMapping("/{id}/confirm")
    @RequiresPurchaseInventoryAdminAccess
    public ResponseEntity<DebitNoteResponseDTO> confirm(@PathVariable Long id) {
        return ResponseEntity.ok(service.confirm(id));
    }

    @PutMapping("/{id}/cancel")
    @RequiresAdminOnly
    public ResponseEntity<DebitNoteResponseDTO> cancel(@PathVariable Long id) {
        return ResponseEntity.ok(service.cancel(id));
    }
}
