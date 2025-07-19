package com.nextgenmanager.nextgenmanager.Inventory.dto;

import com.nextgenmanager.nextgenmanager.Inventory.model.InventoryProcurementStatus;
import com.nextgenmanager.nextgenmanager.Inventory.model.ProcurementDecision;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Date;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class InventoryProcurementOrderDTO {
    private Long id;
    private String itemCode;
    private String itemName;
    private InventoryProcurementStatus status;
    private ProcurementDecision decision;
    private int totalInstances;
    private Long requestId;
    private String createdBy;
    private Date creationDate;
}
