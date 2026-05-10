package com.nextgenmanager.nextgenmanager.purchase.requisition.exception;

public class PurchaseRequisitionNotFoundException extends RuntimeException {
    public PurchaseRequisitionNotFoundException(Long id) {
        super("Purchase requisition not found: " + id);
    }
}
