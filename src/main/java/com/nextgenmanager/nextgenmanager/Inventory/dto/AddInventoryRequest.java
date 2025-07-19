package com.nextgenmanager.nextgenmanager.Inventory.dto;

import com.nextgenmanager.nextgenmanager.Inventory.model.ProcurementDecision;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AddInventoryRequest {

    private int inventoryItemId;

    private ProcurementDecision procurementDecision;

    private long referenceId;

    private double quantity;

    private double costPerUnit;

    private String createdBy;
}
