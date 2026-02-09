package com.nextgenmanager.nextgenmanager.production.mapper;

import com.nextgenmanager.nextgenmanager.production.dto.WorkOrderDTO;
import com.nextgenmanager.nextgenmanager.production.model.WorkOrder;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface WorkOrderMapper {

    @Mapping(target = "workCenter", source ="workCenter.centerCode")
    @Mapping(target = "materials", source ="materials")
    @Mapping(target = "operations", source ="operations")
    @Mapping(target = "inventoryItem", source ="bom.parentInventoryItem")
    @Mapping(target = "status", source ="workOrderStatus")
    WorkOrderDTO toDTO(WorkOrder workOrder);
}
