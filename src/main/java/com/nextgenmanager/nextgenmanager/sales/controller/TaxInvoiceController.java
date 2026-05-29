package com.nextgenmanager.nextgenmanager.sales.controller;

import com.nextgenmanager.nextgenmanager.sales.dto.TaxInvoiceCreateDto;
import com.nextgenmanager.nextgenmanager.sales.dto.TaxInvoiceDto;
import com.nextgenmanager.nextgenmanager.sales.service.TaxInvoiceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import com.nextgenmanager.nextgenmanager.common.security.authorization.RequiresSalesAccess;
import com.nextgenmanager.nextgenmanager.common.security.authorization.RequiresSalesAdminAccess;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api/tax-invoices")
@RequiresSalesAccess
@RequiredArgsConstructor
public class TaxInvoiceController {

    private final TaxInvoiceService taxInvoiceService;
    private final com.nextgenmanager.nextgenmanager.sales.service.TaxInvoicePdfService pdfService;

    @GetMapping("/{id}/pdf")
    public ResponseEntity<byte[]> downloadPdf(@PathVariable Long id) {
        byte[] pdf = pdfService.generatePdf(id);
        TaxInvoiceDto dto = taxInvoiceService.getById(id);
        String filename = "Invoice_" + dto.getInvoiceNumber().replace("/", "_") + ".pdf";
        return ResponseEntity.ok()
                .header("Content-Type", "application/pdf")
                .header("Content-Disposition", "attachment; filename=\"" + filename + "\"")
                .body(pdf);
    }

    @PostMapping
    public ResponseEntity<TaxInvoiceDto> create(@Valid @RequestBody TaxInvoiceCreateDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(taxInvoiceService.createFromSalesOrder(dto));
    }

    @GetMapping
    public ResponseEntity<Page<TaxInvoiceDto>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(taxInvoiceService.list(page, size));
    }

    @GetMapping("/{id}")
    public ResponseEntity<TaxInvoiceDto> getById(@PathVariable Long id) {
        return ResponseEntity.ok(taxInvoiceService.getById(id));
    }

    @GetMapping("/by-sales-order/{soId}")
    public ResponseEntity<List<TaxInvoiceDto>> getBySalesOrder(@PathVariable Long soId) {
        return ResponseEntity.ok(taxInvoiceService.getBySalesOrderId(soId));
    }

    @GetMapping("/next-number")
    public ResponseEntity<String> nextNumber() {
        return ResponseEntity.ok(taxInvoiceService.nextNumber());
    }

    @PostMapping("/{id}/send")
    public ResponseEntity<TaxInvoiceDto> markSent(@PathVariable Long id) {
        return ResponseEntity.ok(taxInvoiceService.markSent(id));
    }

    @PostMapping("/{id}/pay")
    public ResponseEntity<TaxInvoiceDto> markPaid(
            @PathVariable Long id,
            @RequestParam(required = false) BigDecimal amount) {
        return ResponseEntity.ok(taxInvoiceService.markPaid(id, amount));
    }

    @PostMapping("/{id}/cancel")
    @RequiresSalesAdminAccess
    public ResponseEntity<TaxInvoiceDto> cancel(@PathVariable Long id) {
        return ResponseEntity.ok(taxInvoiceService.cancel(id));
    }
}
