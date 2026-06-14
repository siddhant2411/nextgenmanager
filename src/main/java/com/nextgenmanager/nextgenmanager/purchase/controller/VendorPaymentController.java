package com.nextgenmanager.nextgenmanager.purchase.controller;

import com.nextgenmanager.nextgenmanager.common.security.authorization.RequiresPurchaseAccess;
import com.nextgenmanager.nextgenmanager.common.security.authorization.RequiresPurchaseInventoryAdminAccess;
import com.nextgenmanager.nextgenmanager.purchase.dto.VendorPaymentCreateDto;
import com.nextgenmanager.nextgenmanager.purchase.dto.VendorPaymentDto;
import com.nextgenmanager.nextgenmanager.purchase.service.VendorPaymentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/vendor-invoices/{invoiceId}/payments")
@RequiresPurchaseAccess
@RequiredArgsConstructor
public class VendorPaymentController {

    private final VendorPaymentService paymentService;

    @PostMapping
    @RequiresPurchaseInventoryAdminAccess
    public ResponseEntity<VendorPaymentDto> recordPayment(
            @PathVariable Long invoiceId,
            @Valid @RequestBody VendorPaymentCreateDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(paymentService.recordPayment(invoiceId, dto));
    }

    @GetMapping
    public ResponseEntity<List<VendorPaymentDto>> getPayments(@PathVariable Long invoiceId) {
        return ResponseEntity.ok(paymentService.getPaymentsForInvoice(invoiceId));
    }

    @GetMapping("/summary")
    public ResponseEntity<Map<String, BigDecimal>> getPaymentSummary(@PathVariable Long invoiceId) {
        BigDecimal totalPaid = paymentService.getTotalPaidForInvoice(invoiceId);
        return ResponseEntity.ok(Map.of("totalPaid", totalPaid));
    }

    @DeleteMapping("/{paymentId}")
    @RequiresPurchaseInventoryAdminAccess
    public ResponseEntity<Void> deletePayment(
            @PathVariable Long invoiceId,
            @PathVariable Long paymentId) {
        paymentService.deletePayment(paymentId);
        return ResponseEntity.noContent().build();
    }
}
