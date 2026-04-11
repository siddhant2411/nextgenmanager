package com.nextgenmanager.nextgenmanager.bom.dto;

import com.nextgenmanager.nextgenmanager.items.model.InventoryItem;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BomPositionResponse {
    private int id;
    private int position;
    private double quantity;
    private InventoryItem childInventoryItem;
}
