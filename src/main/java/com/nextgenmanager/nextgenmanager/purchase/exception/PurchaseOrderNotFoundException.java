package com.nextgenmanager.nextgenmanager.purchase.exception;

public class PurchaseOrderNotFoundException extends RuntimeException {
    public PurchaseOrderNotFoundException(Long id) {
        super("Purchase order not found: " + id);
    }
}
