package com.nextgenmanager.nextgenmanager.sales.exception;

public class InvalidSalesOrderStateException extends RuntimeException {
    public InvalidSalesOrderStateException(String message) {
        super(message);
    }
}
