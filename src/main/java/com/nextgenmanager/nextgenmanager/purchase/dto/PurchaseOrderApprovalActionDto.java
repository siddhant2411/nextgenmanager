package com.nextgenmanager.nextgenmanager.purchase.dto;

/** Payload for approve / reject actions. reason is required only for reject. */
public record PurchaseOrderApprovalActionDto(String reason) {}
