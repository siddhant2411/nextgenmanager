package com.nextgenmanager.nextgenmanager.purchase.controller;

import com.nextgenmanager.nextgenmanager.purchase.dto.CreateVendorInvoiceRequest;
import com.nextgenmanager.nextgenmanager.purchase.dto.VendorInvoiceDto;
import com.nextgenmanager.nextgenmanager.purchase.dto.VendorInvoiceListDto;
import com.nextgenmanager.nextgenmanager.purchase.service.VendorInvoiceService;
import org.springframework.http.ResponseEntity;
import com.nextgenmanager.nextgenmanager.common.security.authorization.*;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/vendor-invoices")
@RequiresPurchaseAccess
public class VendorInvoiceController {

    private final VendorInvoiceService service;

    public VendorInvoiceController(VendorInvoiceService service) {
        this.service = service;
    }

    @PostMapping
    @RequiresPurchaseInventoryAdminAccess
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
    @RequiresPurchaseInventoryAdminAccess
    public ResponseEntity<VendorInvoiceDto> post(@PathVariable Long id) {
        return ResponseEntity.ok(service.post(id));
    }

    @PutMapping("/{id}/cancel")
    @RequiresAdminOnly
    public ResponseEntity<VendorInvoiceDto> cancel(@PathVariable Long id) {
        return ResponseEntity.ok(service.cancel(id));
    }
}
