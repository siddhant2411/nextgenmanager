package com.nextgenmanager.nextgenmanager.sales.controller;

import com.nextgenmanager.nextgenmanager.common.security.authorization.RequiresAdminOnly;
import com.nextgenmanager.nextgenmanager.common.security.authorization.RequiresSalesAccess;
import com.nextgenmanager.nextgenmanager.common.security.authorization.RequiresSalesAdminAccess;
import com.nextgenmanager.nextgenmanager.sales.dto.CreateSalesCreditNoteRequest;
import com.nextgenmanager.nextgenmanager.sales.dto.SalesCreditNoteListDTO;
import com.nextgenmanager.nextgenmanager.sales.dto.SalesCreditNoteResponseDTO;
import com.nextgenmanager.nextgenmanager.sales.service.SalesCreditNoteService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/sales-credit-notes")
@RequiresSalesAccess
public class SalesCreditNoteController {

    private final SalesCreditNoteService service;

    public SalesCreditNoteController(SalesCreditNoteService service) {
        this.service = service;
    }

    @PostMapping
    @RequiresSalesAdminAccess
    public ResponseEntity<SalesCreditNoteResponseDTO> create(@RequestBody CreateSalesCreditNoteRequest request) {
        return ResponseEntity.ok(service.create(request));
    }

    @GetMapping("/{id}")
    public ResponseEntity<SalesCreditNoteResponseDTO> getById(@PathVariable Long id) {
        return ResponseEntity.ok(service.getById(id));
    }

    @GetMapping
    public ResponseEntity<Page<SalesCreditNoteListDTO>> getAll(
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size) {
        PageRequest pr = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdDate"));
        return ResponseEntity.ok(service.getAll(pr));
    }

    @GetMapping("/by-customer")
    public ResponseEntity<List<SalesCreditNoteListDTO>> getByCustomer(@RequestParam int customerId) {
        return ResponseEntity.ok(service.getByCustomer(customerId));
    }

    @GetMapping("/by-invoice")
    public ResponseEntity<List<SalesCreditNoteListDTO>> getByInvoice(@RequestParam Long taxInvoiceId) {
        return ResponseEntity.ok(service.getByInvoice(taxInvoiceId));
    }

    @GetMapping("/next-number")
    public ResponseEntity<String> nextNumber() {
        return ResponseEntity.ok(service.nextNumber());
    }

    @PutMapping("/{id}/confirm")
    @RequiresSalesAdminAccess
    public ResponseEntity<SalesCreditNoteResponseDTO> confirm(@PathVariable Long id) {
        return ResponseEntity.ok(service.confirm(id));
    }

    @PutMapping("/{id}/cancel")
    @RequiresAdminOnly
    public ResponseEntity<SalesCreditNoteResponseDTO> cancel(@PathVariable Long id) {
        return ResponseEntity.ok(service.cancel(id));
    }
}
