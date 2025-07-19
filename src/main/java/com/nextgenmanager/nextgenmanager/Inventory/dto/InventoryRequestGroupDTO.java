package com.nextgenmanager.nextgenmanager.Inventory.dto;


import com.nextgenmanager.nextgenmanager.Inventory.model.InventoryApprovalStatus;
import com.nextgenmanager.nextgenmanager.Inventory.model.InventoryRequestSource;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import java.util.Date;

@Getter
@Setter
@AllArgsConstructor
public class InventoryRequestGroupDTO {

    private Long id;
    private Long referenceId;
    private int inventoryItemId;
    private String itemCode;
    private String itemName;
    private double totalRequiredQty;
    private double requestedQty;
    private double pendingQty;
    private Date firstRequestedDate;
    private InventoryRequestSource source;
    private String requestedBy;
    private InventoryApprovalStatus approvalStatus;
}
