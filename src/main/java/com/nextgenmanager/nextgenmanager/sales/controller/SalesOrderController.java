package com.nextgenmanager.nextgenmanager.sales.controller;

import com.nextgenmanager.nextgenmanager.sales.dto.*;
import com.nextgenmanager.nextgenmanager.sales.model.SalesOrderStatus;
import com.nextgenmanager.nextgenmanager.sales.service.InvoicePdfService;
import com.nextgenmanager.nextgenmanager.sales.service.SalesOrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import com.nextgenmanager.nextgenmanager.common.security.authorization.RequiresSalesAccess;
import com.nextgenmanager.nextgenmanager.common.security.authorization.RequiresSalesAdminAccess;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/sales-orders")
@RequiresSalesAccess
@RequiredArgsConstructor
public class SalesOrderController {

    private static final Logger logger = LoggerFactory.getLogger(SalesOrderController.class);

    private final SalesOrderService salesOrderService;
    private final InvoicePdfService invoicePdfService;

    // ---- CRUD ----

    @PostMapping
    public ResponseEntity<SalesOrderDto> createSalesOrder(@Valid @RequestBody SalesOrderCreateDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(salesOrderService.createSalesOrder(dto));
    }

    @GetMapping
    public ResponseEntity<Page<SalesOrderDisplayDto>> getAllSalesOrders(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "orderDate") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir,
            @RequestParam(required = false) String orderNumber,
            @RequestParam(required = false) LocalDate orderDate,
            @RequestParam(required = false) String customerName,
            @RequestParam(required = false) String poNumber,
            @RequestParam(required = false) BigDecimal netAmount,
            @RequestParam(required = false) SalesOrderStatus status) {
        return ResponseEntity.ok(salesOrderService.getSalesOrderDisplayList(
                page, size, sortBy, sortDir, orderNumber, orderDate, customerName, poNumber, netAmount, status));
    }

    @GetMapping("/{id}")
    public ResponseEntity<SalesOrderDto> getSalesOrder(@PathVariable Long id) {
        return ResponseEntity.ok(salesOrderService.getSalesOrderById(id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<SalesOrderDto> updateSalesOrder(@PathVariable Long id,
                                                          @Valid @RequestBody SalesOrderCreateDto dto) {
        return ResponseEntity.ok(salesOrderService.updateSalesOrder(id, dto));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteSalesOrder(@PathVariable Long id) {
        salesOrderService.deleteSalesOrder(id);
        return ResponseEntity.noContent().build();
    }

    // ---- Approval workflow ----

    @PostMapping("/{id}/submit")
    public ResponseEntity<SalesOrderDto> submit(@PathVariable Long id) {
        return ResponseEntity.ok(salesOrderService.submit(id));
    }

    @PostMapping("/{id}/approve")
    @RequiresSalesAdminAccess
    public ResponseEntity<SalesOrderDto> approve(@PathVariable Long id) {
        return ResponseEntity.ok(salesOrderService.approve(id));
    }

    @PostMapping("/{id}/reject")
    @RequiresSalesAdminAccess
    public ResponseEntity<SalesOrderDto> reject(@PathVariable Long id,
                                                @RequestBody(required = false) SalesOrderApprovalActionDto dto) {
        return ResponseEntity.ok(salesOrderService.reject(id, dto));
    }

    @PostMapping("/{id}/send")
    public ResponseEntity<SalesOrderDto> send(@PathVariable Long id,
                                              @RequestParam(required = false) String toEmail) {
        return ResponseEntity.ok(salesOrderService.send(id, toEmail));
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<SalesOrderDto> cancel(@PathVariable Long id,
                                                @RequestBody(required = false) SalesOrderApprovalActionDto dto) {
        return ResponseEntity.ok(salesOrderService.cancel(id, dto));
    }

    @PostMapping("/{id}/complete")
    public ResponseEntity<SalesOrderDto> complete(@PathVariable Long id) {
        return ResponseEntity.ok(salesOrderService.complete(id));
    }

    @PostMapping("/{id}/recalculate")
    public ResponseEntity<SalesOrderDto> recalculate(@PathVariable Long id) {
        return ResponseEntity.ok(salesOrderService.recalculate(id));
    }

    // ---- Generic status change (kept for backward compat) ----

    @PostMapping("/{id}/change-status")
    public ResponseEntity<Void> changeStatus(@PathVariable Long id,
                                             @RequestBody SalesOrderStatus salesOrderStatus,
                                             @RequestParam(defaultValue = "true") boolean inventoryAction) {
        salesOrderService.salesOrderStatusChange(id, salesOrderStatus, inventoryAction);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<SalesOrderDto> updateStatus(@PathVariable Long id, @RequestBody StatusUpdateRequest request) {
        return ResponseEntity.ok(salesOrderService.updateStatus(id, request.getStatus()));
    }

    // ---- Queries ----

    @GetMapping("/pending-dispatch")
    public ResponseEntity<List<SalesOrderDisplayDto>> pendingDispatch() {
        return ResponseEntity.ok(salesOrderService.getPendingDispatch());
    }

    @GetMapping("/overdue")
    public ResponseEntity<List<SalesOrderDisplayDto>> overdue() {
        return ResponseEntity.ok(salesOrderService.getOverdue());
    }

    @GetMapping("/analytics")
    public ResponseEntity<Map<String, Object>> analytics() {
        return ResponseEntity.ok(salesOrderService.getSalesAnalytics());
    }

    @GetMapping("/{id}/profitability")
    @RequiresSalesAdminAccess
    public ResponseEntity<SalesOrderProfitabilityDto> getProfitability(@PathVariable Long id) {
        return ResponseEntity.ok(salesOrderService.getProfitability(id));
    }

    @GetMapping("/next-number")
    public ResponseEntity<String> nextNumber() {
        return ResponseEntity.ok(salesOrderService.nextNumber());
    }

    // ---- PDF ----

    @GetMapping("/{id}/pdf")
    public ResponseEntity<byte[]> downloadPdf(@PathVariable Long id) {
        SalesOrderDto order = salesOrderService.getSalesOrderById(id);
        return pdfResponse(invoicePdfService.generateInvoicePdf(id),
                "Invoice-" + order.getOrderNumber() + ".pdf");
    }

    @GetMapping("/{id}/pdf/order-acknowledgement")
    public ResponseEntity<byte[]> downloadOrderAcknowledgement(@PathVariable Long id) {
        SalesOrderDto order = salesOrderService.getSalesOrderById(id);
        return pdfResponse(invoicePdfService.generateOrderAcknowledgementPdf(id),
                "OA-" + order.getOrderNumber() + ".pdf");
    }

    @GetMapping("/{id}/pdf/proforma-invoice")
    public ResponseEntity<byte[]> downloadProformaInvoice(@PathVariable Long id) {
        SalesOrderDto order = salesOrderService.getSalesOrderById(id);
        return pdfResponse(invoicePdfService.generateProformaInvoicePdf(id),
                "PF-" + order.getOrderNumber() + ".pdf");
    }

    private ResponseEntity<byte[]> pdfResponse(byte[] pdf, String filename) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + filename)
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf);
    }
}
