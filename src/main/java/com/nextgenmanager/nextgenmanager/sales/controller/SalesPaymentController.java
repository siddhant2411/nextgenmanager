package com.nextgenmanager.nextgenmanager.sales.controller;

import com.nextgenmanager.nextgenmanager.sales.dto.SalesPaymentCreateDto;
import com.nextgenmanager.nextgenmanager.sales.dto.SalesPaymentDto;
import com.nextgenmanager.nextgenmanager.sales.service.SalesPaymentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import com.nextgenmanager.nextgenmanager.common.security.authorization.RequiresSalesAccess;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/sales-orders/{orderId}/payments")
@RequiresSalesAccess
@RequiredArgsConstructor
public class SalesPaymentController {

    private final SalesPaymentService paymentService;

    @PostMapping
    public ResponseEntity<SalesPaymentDto> recordPayment(
            @PathVariable Long orderId,
            @Valid @RequestBody SalesPaymentCreateDto dto) {
        SalesPaymentDto result = paymentService.recordPayment(orderId, dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(result);
    }

    @GetMapping
    public ResponseEntity<List<SalesPaymentDto>> getPayments(@PathVariable Long orderId) {
        return ResponseEntity.ok(paymentService.getPaymentsForOrder(orderId));
    }

    @GetMapping("/summary")
    public ResponseEntity<Map<String, BigDecimal>> getPaymentSummary(@PathVariable Long orderId) {
        BigDecimal totalPaid = paymentService.getTotalPaidForOrder(orderId);
        return ResponseEntity.ok(Map.of("totalPaid", totalPaid));
    }

    @DeleteMapping("/{paymentId}")
    public ResponseEntity<Void> deletePayment(
            @PathVariable Long orderId,
            @PathVariable Long paymentId) {
        paymentService.deletePayment(paymentId);
        return ResponseEntity.noContent().build();
    }
}
