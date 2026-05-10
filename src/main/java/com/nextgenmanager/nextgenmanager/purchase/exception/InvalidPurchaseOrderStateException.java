package com.nextgenmanager.nextgenmanager.purchase.exception;

public class InvalidPurchaseOrderStateException extends RuntimeException {
    public InvalidPurchaseOrderStateException(String message) {
        super(message);
    }
}
