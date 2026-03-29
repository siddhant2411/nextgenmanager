package com.nextgenmanager.nextgenmanager.production.helper;

public class InvalidTransitionException extends RuntimeException{
    public InvalidTransitionException(String message) {
        super(message);
    }
}
