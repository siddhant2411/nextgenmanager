package com.nextgenmanager.nextgenmanager.purchase.controller;

import com.nextgenmanager.nextgenmanager.purchase.dto.CreateVendorInvoiceRequest;
import com.nextgenmanager.nextgenmanager.purchase.dto.VendorInvoiceDto;
import com.nextgenmanager.nextgenmanager.purchase.dto.VendorInvoiceListDto;
import com.nextgenmanager.nextgenmanager.purchase.service.VendorInvoiceService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/vendor-invoices")
@PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN','INVENTORY_ADMIN','INVENTORY_USER','USER')")
public class VendorInvoiceController {

    private final VendorInvoiceService service;

    public VendorInvoiceController(VendorInvoiceService service) {
        this.service = service;
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN','INVENTORY_ADMIN')")
    public ResponseEntity<VendorInvoiceDto> create(@RequestBody CreateVendorInvoiceRequest request) {
        return ResponseEntity.ok(service.create(request));
    }

    @GetMapping("/{id}")
    public ResponseEntity<VendorInvoiceDto> getById(@PathVariable Long id) {
        return ResponseEntity.ok(service.getById(id));
    }

    @GetMapping
    public ResponseEntity<List<VendorInvoiceListDto>> getByPo(@RequestParam Long poId) {
        return ResponseEntity.ok(service.getByPo(poId));
    }

    @PutMapping("/{id}/post")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN','INVENTORY_ADMIN')")
    public ResponseEntity<VendorInvoiceDto> post(@PathVariable Long id) {
        return ResponseEntity.ok(service.post(id));
    }

    @PutMapping("/{id}/cancel")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN')")
    public ResponseEntity<VendorInvoiceDto> cancel(@PathVariable Long id) {
        return ResponseEntity.ok(service.cancel(id));
    }
}
