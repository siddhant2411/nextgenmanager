package com.nextgenmanager.nextgenmanager.bom.dto;

import lombok.*;

import java.math.BigDecimal;

/**
 * Response/breakdown view of a {@link com.nextgenmanager.nextgenmanager.bom.model.BomCostLine}.
 * Used both for edit-load (BomDTO.costLines) and the cost breakdown (additionalCosts).
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BomCostLineDTO {
    private Integer id;
    private Integer inventoryItemId;
    private String itemCode;
    private String itemName;
    private BigDecimal amount;
    private int position;
}
