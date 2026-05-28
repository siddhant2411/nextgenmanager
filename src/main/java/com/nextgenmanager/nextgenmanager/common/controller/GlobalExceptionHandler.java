package com.nextgenmanager.nextgenmanager.common.controller;

import com.nextgenmanager.nextgenmanager.bom.service.BomServiceException;
import com.nextgenmanager.nextgenmanager.bom.service.BusinessException;
import com.nextgenmanager.nextgenmanager.bom.service.InvalidDataException;
import com.nextgenmanager.nextgenmanager.bom.service.ResourceNotFoundException;
import com.nextgenmanager.nextgenmanager.production.helper.InvalidTransitionException;
import com.nextgenmanager.nextgenmanager.purchase.exception.InvalidPurchaseOrderStateException;
import com.nextgenmanager.nextgenmanager.purchase.exception.PurchaseOrderNotFoundException;
import com.nextgenmanager.nextgenmanager.purchase.requisition.exception.InvalidPurchaseRequisitionStateException;
import com.nextgenmanager.nextgenmanager.purchase.requisition.exception.PurchaseRequisitionNotFoundException;
import com.nextgenmanager.nextgenmanager.sales.exception.InvalidSalesOrderStateException;
import com.nextgenmanager.nextgenmanager.sales.exception.SalesOrderNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    public record ApiError(
            OffsetDateTime timestamp,
            int status,
            String error,
            String message,
            String path,
            Map<String, List<String>> fieldErrors
    ) {}

    private ApiError buildError(HttpStatus status, String message, HttpServletRequest request) {
        return new ApiError(
                OffsetDateTime.now(),
                status.value(),
                status.getReasonPhrase(),
                message,
                request.getRequestURI(),
                null
        );
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiError> handleAccessDenied(AccessDeniedException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(buildError(HttpStatus.FORBIDDEN, "You do not have permission to access this resource", request));
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ApiError> handleBadCredentials(BadCredentialsException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(buildError(HttpStatus.UNAUTHORIZED, "Invalid username or password", request));
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiError> handleResourceNotFound(ResourceNotFoundException ex, HttpServletRequest request) {
        String message = Optional.ofNullable(ex.getMessage()).filter(m -> !m.isBlank()).orElse("Resource not found");
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(buildError(HttpStatus.NOT_FOUND, message, request));
    }

    @ExceptionHandler(SalesOrderNotFoundException.class)
    public ResponseEntity<ApiError> handleSalesOrderNotFound(SalesOrderNotFoundException ex, HttpServletRequest request) {
        String message = Optional.ofNullable(ex.getMessage()).filter(m -> !m.isBlank()).orElse("Sales order not found");
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(buildError(HttpStatus.NOT_FOUND, message, request));
    }

    @ExceptionHandler(PurchaseOrderNotFoundException.class)
    public ResponseEntity<ApiError> handlePurchaseOrderNotFound(PurchaseOrderNotFoundException ex, HttpServletRequest request) {
        String message = Optional.ofNullable(ex.getMessage()).filter(m -> !m.isBlank()).orElse("Purchase order not found");
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(buildError(HttpStatus.NOT_FOUND, message, request));
    }

    @ExceptionHandler(PurchaseRequisitionNotFoundException.class)
    public ResponseEntity<ApiError> handlePurchaseRequisitionNotFound(PurchaseRequisitionNotFoundException ex, HttpServletRequest request) {
        String message = Optional.ofNullable(ex.getMessage()).filter(m -> !m.isBlank()).orElse("Purchase requisition not found");
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(buildError(HttpStatus.NOT_FOUND, message, request));
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiError> handleBusinessException(BusinessException ex, HttpServletRequest request) {
        String message = Optional.ofNullable(ex.getMessage()).filter(m -> !m.isBlank()).orElse("Business rule violation");
        return ResponseEntity.badRequest()
                .body(buildError(HttpStatus.BAD_REQUEST, message, request));
    }

    @ExceptionHandler(InvalidDataException.class)
    public ResponseEntity<ApiError> handleInvalidData(InvalidDataException ex, HttpServletRequest request) {
        String message = Optional.ofNullable(ex.getMessage()).filter(m -> !m.isBlank()).orElse("Invalid data provided");
        return ResponseEntity.badRequest()
                .body(buildError(HttpStatus.BAD_REQUEST, message, request));
    }

    @ExceptionHandler(InvalidSalesOrderStateException.class)
    public ResponseEntity<ApiError> handleInvalidSalesOrderState(InvalidSalesOrderStateException ex, HttpServletRequest request) {
        String message = Optional.ofNullable(ex.getMessage()).filter(m -> !m.isBlank()).orElse("This action is not allowed for the current sales order status");
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(buildError(HttpStatus.CONFLICT, message, request));
    }

    @ExceptionHandler(InvalidPurchaseOrderStateException.class)
    public ResponseEntity<ApiError> handleInvalidPurchaseOrderState(InvalidPurchaseOrderStateException ex, HttpServletRequest request) {
        String message = Optional.ofNullable(ex.getMessage()).filter(m -> !m.isBlank()).orElse("This action is not allowed for the current purchase order status");
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(buildError(HttpStatus.CONFLICT, message, request));
    }

    @ExceptionHandler(InvalidPurchaseRequisitionStateException.class)
    public ResponseEntity<ApiError> handleInvalidPurchaseRequisitionState(InvalidPurchaseRequisitionStateException ex, HttpServletRequest request) {
        String message = Optional.ofNullable(ex.getMessage()).filter(m -> !m.isBlank()).orElse("This action is not allowed for the current requisition status");
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(buildError(HttpStatus.CONFLICT, message, request));
    }

    @ExceptionHandler(InvalidTransitionException.class)
    public ResponseEntity<ApiError> handleInvalidTransition(InvalidTransitionException ex, HttpServletRequest request) {
        String message = Optional.ofNullable(ex.getMessage()).filter(m -> !m.isBlank()).orElse("This state transition is not allowed");
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(buildError(HttpStatus.CONFLICT, message, request));
    }

    @ExceptionHandler(BomServiceException.class)
    public ResponseEntity<ApiError> handleBomServiceException(BomServiceException ex, HttpServletRequest request) {
        logger.error("BOM service error at {}", request.getRequestURI(), ex);
        String message = Optional.ofNullable(ex.getMessage()).filter(m -> !m.isBlank()).orElse("BOM operation failed");
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(buildError(HttpStatus.INTERNAL_SERVER_ERROR, message, request));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiError> handleIllegalArgument(IllegalArgumentException ex, HttpServletRequest request) {
        String message = Optional.ofNullable(ex.getMessage()).filter(m -> !m.isBlank()).orElse("Invalid request");
        return ResponseEntity.badRequest()
                .body(buildError(HttpStatus.BAD_REQUEST, message, request));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ApiError> handleIllegalState(IllegalStateException ex, HttpServletRequest request) {
        String message = Optional.ofNullable(ex.getMessage()).filter(m -> !m.isBlank()).orElse("Operation not allowed in the current state");
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(buildError(HttpStatus.CONFLICT, message, request));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidationException(MethodArgumentNotValidException ex, HttpServletRequest request) {
        Map<String, List<String>> fieldErrors = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .collect(Collectors.groupingBy(
                        fe -> fe.getField(),
                        Collectors.mapping(fe -> fe.getDefaultMessage(), Collectors.toList())
                ));

        String summary = fieldErrors.entrySet().stream()
                .map(e -> e.getKey() + ": " + String.join(", ", e.getValue()))
                .collect(Collectors.joining("; "));

        ApiError apiError = new ApiError(
                OffsetDateTime.now(),
                HttpStatus.BAD_REQUEST.value(),
                HttpStatus.BAD_REQUEST.getReasonPhrase(),
                summary.isBlank() ? "Validation failed" : summary,
                request.getRequestURI(),
                fieldErrors
        );

        return ResponseEntity.badRequest().body(apiError);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleInvalidJson(HttpMessageNotReadableException ex, HttpServletRequest request) {
        return ResponseEntity.badRequest()
                .body(buildError(HttpStatus.BAD_REQUEST, "Malformed JSON request", request));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiError> handleTypeMismatch(MethodArgumentTypeMismatchException ex, HttpServletRequest request) {
        return ResponseEntity.badRequest()
                .body(buildError(HttpStatus.BAD_REQUEST, "Invalid value for parameter: " + ex.getName(), request));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiError> handleDatabaseError(DataIntegrityViolationException ex, HttpServletRequest request) {
        String rootMessage = ex.getMostSpecificCause() != null ? ex.getMostSpecificCause().getMessage() : "";

        String userMessage;
        if (rootMessage.contains("sales_order") && rootMessage.contains("ordernumber")) {
            userMessage = "A sales order with this order number already exists.";
        } else if (rootMessage.contains("workorder") && rootMessage.contains("workordernumber")) {
            userMessage = "A work order with this number already exists.";
        } else if (rootMessage.contains("enquiry") && rootMessage.contains("enqno")) {
            userMessage = "An enquiry with this number already exists.";
        } else if (rootMessage.contains("quotation") && rootMessage.contains("qtnno")) {
            userMessage = "A quotation with this number already exists.";
        } else if (rootMessage.contains("inventoryitem") && rootMessage.contains("itemcode")) {
            userMessage = "An item with this item code already exists.";
        } else if (rootMessage.contains("contact") && rootMessage.contains("gstnumber")) {
            userMessage = "A contact with this GST number already exists.";
        } else if (rootMessage.contains("unique") || rootMessage.contains("duplicate")) {
            userMessage = "A record with the same unique value already exists.";
        } else {
            userMessage = "This operation could not be completed due to a data conflict.";
        }

        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(buildError(HttpStatus.CONFLICT, userMessage, request));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleGeneric(Exception ex, HttpServletRequest request) {
        logger.error("Unhandled exception at {}", request.getRequestURI(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(buildError(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred. Please contact support.", request));
    }
}
