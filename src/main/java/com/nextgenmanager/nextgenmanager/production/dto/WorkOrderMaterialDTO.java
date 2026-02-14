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

    private MaterialIssueStatus issueStatus;

}
