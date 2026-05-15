package com.nextgenmanager.nextgenmanager.sales.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DeliveryNoteItemDetailDto {
    private Long id;
    private Integer inventoryItemId;
    private String itemName;
    private int quantityDelivered;
    private java.util.List<String> batchNumbers;
    private java.util.List<String> serialNumbers;
}
