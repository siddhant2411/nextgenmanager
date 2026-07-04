package com.nextgenmanager.nextgenmanager.bom.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.nextgenmanager.nextgenmanager.bom.model.BomCostLine;
import com.nextgenmanager.nextgenmanager.items.model.InventoryItem;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Inbound BOM cost line. Always references an existing master item by id (BOM never creates
 * items); {@code amount} is the flat per-BOM price for that item.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class BomCostLineRequestDTO {

    private Integer inventoryItemId;
    private BigDecimal amount;
    private Integer position;

    public BomCostLine toEntity() {
        BomCostLine entity = new BomCostLine();
        entity.setAmount(amount != null ? amount : BigDecimal.ZERO);
        if (position != null) {
            entity.setPosition(position);
        }
        if (inventoryItemId != null) {
            InventoryItem item = new InventoryItem();
            item.setInventoryItemId(inventoryItemId);
            entity.setInventoryItem(item);
        }
        return entity;
    }
}
