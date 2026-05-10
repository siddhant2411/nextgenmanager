package com.nextgenmanager.nextgenmanager.purchase.requisition.service;

import com.nextgenmanager.nextgenmanager.purchase.dto.PurchaseOrderDto;
import com.nextgenmanager.nextgenmanager.purchase.requisition.dto.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface PurchaseRequisitionService {

    PurchaseRequisitionDto create(PurchaseRequisitionCreateDto dto);

    PurchaseRequisitionDto getById(Long id);

    Page<PurchaseRequisitionListDto> list(String status, String approvalStatus, String source, Pageable pageable);

    PurchaseRequisitionDto update(Long id, PurchaseRequisitionUpdateDto dto);

    void delete(Long id);

    // ── State transitions ──────────────────────────────────────────────────────

    PurchaseRequisitionDto submit(Long id);

    PurchaseRequisitionDto approve(Long id);

    PurchaseRequisitionDto reject(Long id, PurchaseRequisitionApprovalActionDto dto);

    PurchaseRequisitionDto cancel(Long id, PurchaseRequisitionApprovalActionDto dto);

    // ── Conversion ─────────────────────────────────────────────────────────────

    PurchaseOrderDto convertToPurchaseOrder(Long id, ConvertToPoRequestDto dto);

    /** Generate a draft PR from unfulfilled WorkOrderMaterial lines. */
    PurchaseRequisitionDto generateFromWorkOrder(Long workOrderId);

    String nextNumber();
}
