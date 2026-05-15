package com.nextgenmanager.nextgenmanager.purchase.service;

import com.nextgenmanager.nextgenmanager.purchase.dto.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface PurchaseOrderService {

    PurchaseOrderDto create(PurchaseOrderCreateDto dto);

    PurchaseOrderDto getById(Long id);

    Page<PurchaseOrderListDto> list(String status, String approvalStatus, Integer vendorId, Pageable pageable);

    /** Returns all POs in SENT or PARTIALLY_RECEIVED status, with daysOverdue populated where applicable. */
    List<PurchaseOrderListDto> getPendingReceipt();

    /** Returns POs past their expectedDeliveryDate that are still SENT or PARTIALLY_RECEIVED. */
    List<PurchaseOrderListDto> getOverduePOs();

    PurchaseOrderDto update(Long id, PurchaseOrderUpdateDto dto);

    void delete(Long id);

    // ── State transitions ──────────────────────────────────────────────────────

    PurchaseOrderDto submit(Long id);

    PurchaseOrderDto approve(Long id);

    PurchaseOrderDto reject(Long id, PurchaseOrderApprovalActionDto dto);

    PurchaseOrderDto send(Long id);

    PurchaseOrderDto cancel(Long id, PurchaseOrderApprovalActionDto dto);

    /** Transition RECEIVED → COMPLETED (fully invoiced and closed). */
    PurchaseOrderDto complete(Long id);

    PurchaseOrderDto recalculate(Long id);

    // ── Utilities ──────────────────────────────────────────────────────────────

    String nextNumber();

    byte[] generatePdf(Long id);

    /** Record that the PO was emailed to the vendor. */
    PurchaseOrderDto markEmailSent(Long id, String toEmail);

    PurchaseAnalyticsDto getPurchaseAnalytics();
}
