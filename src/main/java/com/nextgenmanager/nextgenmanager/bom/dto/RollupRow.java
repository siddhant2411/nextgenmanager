package com.nextgenmanager.nextgenmanager.bom.dto;

import com.nextgenmanager.nextgenmanager.items.model.InventoryItem;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;

@Data
@Getter
@Setter
public class RollupRow {

    private InventoryItem item;
    private double totalQty;

}
