package com.nextgenmanager.nextgenmanager.Inventory.dto;

import com.nextgenmanager.nextgenmanager.Inventory.model.InventoryRequestSource;
import com.nextgenmanager.nextgenmanager.Inventory.model.ProcurementDecision;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class ApproveRequestDTO {
    private List<Long> instanceIds;
    private InventoryRequestSource requestSource;
    private Long orderReferenceId;
}