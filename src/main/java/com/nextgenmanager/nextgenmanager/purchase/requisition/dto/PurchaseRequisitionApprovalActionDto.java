package com.nextgenmanager.nextgenmanager.purchase.requisition.dto;

/** Payload for reject / cancel actions. Reason required for reject. */
public record PurchaseRequisitionApprovalActionDto(String reason) {}
