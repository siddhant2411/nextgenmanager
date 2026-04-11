package com.nextgenmanager.nextgenmanager.bom.dto;


import com.nextgenmanager.nextgenmanager.items.model.UOM;
import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class BomPositionDTO {

    private int positionId;
    private int childInventoryItemId;
    private String itemName;
    private String itemCode;
    private String drawingNumber;
    private UOM uom;
    private int position;
    private double quantity;
    private BigDecimal scrapPercentage;
    private boolean hasActiveBom;
    private Integer activeBomId;

    /** ID of the routing operation that consumes this component. Null = no specific gate. */
    private Long routingOperationId;

    /** Display name of the assigned routing operation. Null when not assigned. */
    private String routingOperationName;

}
