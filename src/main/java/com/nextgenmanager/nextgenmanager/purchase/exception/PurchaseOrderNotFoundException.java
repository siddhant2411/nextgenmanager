package com.nextgenmanager.nextgenmanager.purchase.exception;

public class PurchaseOrderNotFoundException extends RuntimeException {

    public PurchaseOrderNotFoundException(Long id) {
        super("Purchase order not found: " + id);
    }

    /** Lookup by the number a human quotes, rather than by surrogate id. */
    public PurchaseOrderNotFoundException(String purchaseOrderNumber) {
        super("Purchase order not found: " + purchaseOrderNumber);
    }
}
