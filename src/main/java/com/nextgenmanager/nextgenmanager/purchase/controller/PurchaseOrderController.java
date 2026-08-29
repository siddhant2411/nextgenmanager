package com.nextgenmanager.nextgenmanager.purchase.controller;

import com.nextgenmanager.nextgenmanager.bom.service.InvalidDataException;
import com.nextgenmanager.nextgenmanager.purchase.dto.*;
import com.nextgenmanager.nextgenmanager.purchase.service.PurchaseOrderService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.stream.Collectors;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import com.nextgenmanager.nextgenmanager.common.security.authorization.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/purchase-orders")
@RequiresPurchaseAccess
public class PurchaseOrderController {

    private final PurchaseOrderService service;

    public PurchaseOrderController(PurchaseOrderService service) {
        this.service = service;
    }

    // ── CRUD ──────────────────────────────────────────────────────────────────

    @PostMapping
    public ResponseEntity<PurchaseOrderDto> create(@RequestBody PurchaseOrderCreateDto dto) {
        return ResponseEntity.ok(service.create(dto));
    }

    /**
     * Lists purchase orders. Every filter supplied narrows the result; see
     * {@link PurchaseOrderFilter} for the full set.
     *
     * <p>Unknown parameters are rejected rather than ignored. Spring binds query parameters by
     * name and silently drops the rest, so {@code ?poNo=7} used to come back as page one of the
     * entire table -- twenty unrelated POs presented as the answer to a search for one. A typo is
     * a 400 naming the parameter now.
     */
    @GetMapping
    public ResponseEntity<Page<PurchaseOrderListDto>> list(
            @ModelAttribute PurchaseOrderFilter filter,
            HttpServletRequest request,
            @PageableDefault(size = 20, sort = "createdDate") Pageable pageable) {
        rejectUnknownParams(request);
        return ResponseEntity.ok(service.list(filter, pageable));
    }

    private static void rejectUnknownParams(HttpServletRequest request) {
        List<String> unknown = request.getParameterMap().keySet().stream()
                .filter(name -> !PurchaseOrderFilter.KNOWN_PARAMS.contains(name))
                .sorted()
                .toList();
        if (!unknown.isEmpty()) {
            throw new InvalidDataException("Unknown query parameter(s): " + String.join(", ", unknown)
                    + ". Supported: " + PurchaseOrderFilter.KNOWN_PARAMS.stream()
                    .sorted().collect(Collectors.joining(", ")));
        }
    }

    /**
     * Fetches one PO by the number people quote, rather than by surrogate id.
     *
     * <p>The number travels as a query parameter because it contains slashes --
     * {@code PO/2026-27/0007}. As a path variable it would either split across segments or need
     * percent-encoding that the container rejects by default.
     */
    @GetMapping("/by-number")
    public ResponseEntity<PurchaseOrderDto> getByNumber(@RequestParam String number) {
        return ResponseEntity.ok(service.getByNumber(number));
    }

    @GetMapping("/{id}")
    public ResponseEntity<PurchaseOrderDto> getById(@PathVariable Long id) {
        return ResponseEntity.ok(service.getById(id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<PurchaseOrderDto> update(@PathVariable Long id,
                                                   @RequestBody PurchaseOrderUpdateDto dto) {
        return ResponseEntity.ok(service.update(id, dto));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }

    // ── State transitions ─────────────────────────────────────────────────────

    @PostMapping("/{id}/submit")
    public ResponseEntity<PurchaseOrderDto> submit(@PathVariable Long id) {
        return ResponseEntity.ok(service.submit(id));
    }

    @PostMapping("/{id}/approve")
    @RequiresPurchaseAdminAccess
    public ResponseEntity<PurchaseOrderDto> approve(@PathVariable Long id) {
        return ResponseEntity.ok(service.approve(id));
    }

    @PostMapping("/{id}/reject")
    @RequiresPurchaseAdminAccess
    public ResponseEntity<PurchaseOrderDto> reject(@PathVariable Long id,
                                                   @RequestBody PurchaseOrderApprovalActionDto dto) {
        return ResponseEntity.ok(service.reject(id, dto));
    }

    @PostMapping("/{id}/send")
    public ResponseEntity<PurchaseOrderDto> send(@PathVariable Long id) {
        return ResponseEntity.ok(service.send(id));
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<PurchaseOrderDto> cancel(@PathVariable Long id,
                                                   @RequestBody PurchaseOrderApprovalActionDto dto) {
        return ResponseEntity.ok(service.cancel(id, dto));
    }

    @PostMapping("/{id}/complete")
    public ResponseEntity<PurchaseOrderDto> complete(@PathVariable Long id) {
        return ResponseEntity.ok(service.complete(id));
    }

    @PostMapping("/{id}/recalculate")
    public ResponseEntity<PurchaseOrderDto> recalculate(@PathVariable Long id) {
        return ResponseEntity.ok(service.recalculate(id));
    }

    // ── Inventory receipt helpers ─────────────────────────────────────────────

    @GetMapping("/pending-receipt")
    @RequiresPurchaseInventoryAdminAccess
    public ResponseEntity<List<PurchaseOrderListDto>> pendingReceipt() {
        return ResponseEntity.ok(service.getPendingReceipt());
    }

    @GetMapping("/overdue")
    @RequiresPurchaseInventoryAdminAccess
    public ResponseEntity<List<PurchaseOrderListDto>> overduePOs() {
        return ResponseEntity.ok(service.getOverduePOs());
    }

    @GetMapping("/analytics")
    @RequiresPurchaseAdminAccess
    public ResponseEntity<PurchaseAnalyticsDto> analytics() {
        return ResponseEntity.ok(service.getPurchaseAnalytics());
    }

    // ── Utilities ─────────────────────────────────────────────────────────────

    @GetMapping("/next-number")
    public ResponseEntity<String> nextNumber() {
        return ResponseEntity.ok(service.nextNumber());
    }

    @GetMapping("/{id}/pdf")
    public ResponseEntity<byte[]> pdf(@PathVariable Long id) {
        byte[] pdf = service.generatePdf(id);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"PO-" + id + ".pdf\"")
                .contentType(MediaType.parseMediaType("application/pdf"))
                .body(pdf);
    }

    /** POST /{id}/mark-email-sent — record that PO was emailed to vendor. */
    @PostMapping("/{id}/mark-email-sent")
    public ResponseEntity<PurchaseOrderDto> markEmailSent(
            @PathVariable Long id,
            @RequestBody java.util.Map<String, String> body) {
        return ResponseEntity.ok(service.markEmailSent(id, body.get("toEmail")));
    }
}
