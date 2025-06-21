package com.nextgenmanager.nextgenmanager.production.DTO;


import com.nextgenmanager.nextgenmanager.Inventory.model.InventoryInstance;
import com.nextgenmanager.nextgenmanager.bom.model.Bom;
import com.nextgenmanager.nextgenmanager.production.model.InventoryStatus;
import com.nextgenmanager.nextgenmanager.production.model.WorkOrderProduction;
import com.nextgenmanager.nextgenmanager.production.model.WorkOrderStatus;
import com.nextgenmanager.nextgenmanager.sales.model.SalesOrder;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class WorkOrderProductionRequestMapper {

    private double quantity;

    private InventoryInstance selectedItem;

    private Bom bom;

    private SalesOrder salesOrder;

    private Date dueDate;

    private WorkOrderProduction parentWorkOrder;

    private boolean isCreateChildItems;

    private WorkOrderStatus status;

}
