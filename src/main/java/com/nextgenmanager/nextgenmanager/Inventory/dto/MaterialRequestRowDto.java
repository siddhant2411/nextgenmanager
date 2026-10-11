package com.nextgenmanager.nextgenmanager.Inventory.dto;

import com.nextgenmanager.nextgenmanager.Inventory.model.InventoryApprovalStatus;
import com.nextgenmanager.nextgenmanager.Inventory.model.InventoryRequestSource;

import java.math.BigDecimal;
import java.util.Date;

/**
 * One row of the stores' material request register: what was asked for, by which document, what
 * the stores did about it and when.
 *
 * <p>Carries everything the register prints so the screen never has to open the request, its item
 * or its source document to draw a row.
 */
public record MaterialRequestRowDto(
        Long id,
        String referenceNumber,
        InventoryRequestSource source,
        // The document that raised it, by id and by the number a person would recognise.
        Long sourceId,
        String sourceNumber,
        Integer itemId,
        String itemCode,
        String itemName,
        String uom,
        BigDecimal requestedQty,
        // Issued or reserved so far: the approved quantity of a work order request, the reserved
        // part of a sales order's.
        BigDecimal issuedQty,
        BigDecimal remainingQty,
        // Free stock of the item right now, company-wide. Only meaningful while the row is open.
        BigDecimal availableQty,
        // True when the row is still open and there is not enough free stock to issue the rest.
        boolean stockShort,
        // Code of the warehouse the material is wanted in; null where the request names none.
        String warehouseCode,
        InventoryApprovalStatus status,
        String requestedBy,
        Date requestedDate,
        String remarks,
        // Who approved or rejected it, when, and why it was rejected.
        String decidedBy,
        Date decidedDate,
        String rejectionReason,
        // False for a sales order's reservation, which is settled by that order's pick and challan
        // and is shown here for information only, and for any row already decided.
        boolean actionable
) {}
