package com.nextgenmanager.nextgenmanager.production.mapper;

import com.nextgenmanager.nextgenmanager.bom.mapper.routing.RoutingMapper;
import com.nextgenmanager.nextgenmanager.production.dto.WorkOrderDTO;
import com.nextgenmanager.nextgenmanager.production.dto.WorkOrderLineDTO;
import com.nextgenmanager.nextgenmanager.production.dto.WorkOrderMaterialDTO;
import com.nextgenmanager.nextgenmanager.production.dto.WorkOrderOperationDTO;
import com.nextgenmanager.nextgenmanager.production.model.WorkOrder;
import com.nextgenmanager.nextgenmanager.production.model.WorkOrderLine;
import com.nextgenmanager.nextgenmanager.production.model.WorkOrderMaterial;
import com.nextgenmanager.nextgenmanager.production.model.WorkOrderOperation;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring", uses = {RoutingMapper.class, WorkOrderLabourEntryMapper.class, WorkOrderQaEntryMapper.class})
public interface WorkOrderMapper {

    @Mapping(target = "workCenter", source = "workCenter.centerCode")
    @Mapping(target = "materials", source = "materials")
    @Mapping(target = "operations", source = "operations")
    @Mapping(target = "inventoryItem", source = "bom.parentInventoryItem")
    @Mapping(target = "status", source = "workOrderStatus")
    @Mapping(target = "routing", source = "routing")
    @Mapping(target = "lines", source = "lines")
    WorkOrderDTO toDTO(WorkOrder workOrder);

    /**
     * Explicit per-material mapping so MapStruct populates the two new
     * operation-gate fields when it generates the materials list mapping.
     */
    @Mapping(target = "workOrderOperationId", source = "workOrderOperation.id")
    @Mapping(target = "operationName", source = "workOrderOperation.operationName")
    @Mapping(target = "component.standardCost", source = "component.productFinanceSettings.standardCost")
    @Mapping(target = "workOrderLineId", source = "workOrderLine.id")
    @Mapping(target = "lineNumber", source = "workOrderLine.lineNumber")
    @Mapping(target = "lineItemCode", source = "workOrderLine.inventoryItem.itemCode")
    @Mapping(target = "lineItemName", source = "workOrderLine.inventoryItem.name")
    WorkOrderMaterialDTO toMaterialDTO(WorkOrderMaterial material);

    /**
     * Explicit per-operation mapping, so the list mapping above carries the owning line. Without
     * it an operation arrives at the client as a bare sequence number — and sequences restart per
     * line, so "10" on a two-item work order names two different operations.
     */
    @Mapping(target = "workOrderLineId", source = "workOrderLine.id")
    @Mapping(target = "lineNumber", source = "workOrderLine.lineNumber")
    @Mapping(target = "lineItemCode", source = "workOrderLine.inventoryItem.itemCode")
    @Mapping(target = "lineItemName", source = "workOrderLine.inventoryItem.name")
    WorkOrderOperationDTO toOperationDTO(WorkOrderOperation operation);

    /** Line mapping, flattening the BOM/routing references the client needs by id. */
    @Mapping(target = "bomId", source = "bom.id")
    @Mapping(target = "bomName", source = "bom.bomName")
    @Mapping(target = "routingId", source = "routing.id")
    WorkOrderLineDTO toLineDTO(WorkOrderLine line);
}
