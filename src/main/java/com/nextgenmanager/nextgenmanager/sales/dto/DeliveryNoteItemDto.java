package com.nextgenmanager.nextgenmanager.sales.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DeliveryNoteItemDto {
    private Integer inventoryItemId;
    private java.math.BigDecimal quantityDelivered;
    private java.util.List<Long> allocatedInstanceIds;

}
