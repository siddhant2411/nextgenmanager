package com.nextgenmanager.nextgenmanager.production.mapper;

import com.nextgenmanager.nextgenmanager.production.dto.WorkOrderDTO;
import com.nextgenmanager.nextgenmanager.production.dto.WorkOrderMaterialDTO;
import com.nextgenmanager.nextgenmanager.production.model.WorkOrder;
import com.nextgenmanager.nextgenmanager.production.model.WorkOrderMaterial;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring", uses = {RoutingMapper.class})
public interface WorkOrderMapper {

    @Mapping(target = "workCenter", source = "workCenter.centerCode")
    @Mapping(target = "materials", source = "materials")
    @Mapping(target = "operations", source = "operations")
    @Mapping(target = "inventoryItem", source = "bom.parentInventoryItem")
    @Mapping(target = "status", source = "workOrderStatus")
    @Mapping(target = "routing", source = "routing")
    WorkOrderDTO toDTO(WorkOrder workOrder);

    /**
     * Explicit per-material mapping so MapStruct populates the two new
     * operation-gate fields when it generates the materials list mapping.
     */
    @Mapping(target = "workOrderOperationId", source = "workOrderOperation.id")
    @Mapping(target = "operationName", source = "workOrderOperation.operationName")
    WorkOrderMaterialDTO toMaterialDTO(WorkOrderMaterial material);
}
