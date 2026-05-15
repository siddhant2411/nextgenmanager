package com.nextgenmanager.nextgenmanager.sales.exception;

public class SalesOrderNotFoundException extends RuntimeException {
    public SalesOrderNotFoundException(Long id) {
        super("Sales order not found: " + id);
    }
    public SalesOrderNotFoundException(String message) {
        super(message);
    }
}
