package com.nextgenmanager.nextgenmanager.production.dto;

import com.nextgenmanager.nextgenmanager.items.DTO.InventoryItemDTO;
import com.nextgenmanager.nextgenmanager.items.model.InventoryItem;
import com.nextgenmanager.nextgenmanager.production.enums.MaterialIssueStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class WorkOrderMaterialDTO {

    private Long id;

    private InventoryItemDTO component;

    private BigDecimal netRequiredQuantity;

    private BigDecimal plannedRequiredQuantity;

    private BigDecimal issuedQuantity;

    private BigDecimal scrappedQuantity;

    private BigDecimal consumedQuantity;

    private MaterialIssueStatus issueStatus;

    /** ID of the work-order operation this material is gated behind. Null = no gate. */
    private Long workOrderOperationId;

    /** Name of that operation, for display. Null when not gated. */
    private String operationName;

    // --- Material Request Details ---
    private Long inventoryRequestId;
    private String mrStatus;
    private BigDecimal mrApprovedQuantity;
    private String mrRejectionReason;

    // --- Reorder Summary ---
    /** Total quantity approved across all reorder MRs (APPROVED + PARTIAL approvedQty). */
    private BigDecimal approvedReorderQuantity;

    /** plannedRequiredQuantity + approvedReorderQuantity — the actual cap for issuance. */
    private BigDecimal effectiveRequiredQuantity;

    /** Number of reorder requests ever submitted for this material. */
    private int reorderCount;

}
