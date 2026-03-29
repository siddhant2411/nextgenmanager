package com.nextgenmanager.nextgenmanager.production.mapper;

import com.nextgenmanager.nextgenmanager.production.dto.WorkOrderDTO;
import com.nextgenmanager.nextgenmanager.production.dto.WorkOrderListDTO;
import com.nextgenmanager.nextgenmanager.production.model.WorkOrder;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface WorkOrderListMapper {

    @Mapping(target = "workCenter", source ="workCenter.centerCode")
    @Mapping(target = "parentWorkOrderNumber", source ="parentWorkOrder.workOrderNumber")
    @Mapping(target = "salesOrderNumber", source ="salesOrder.orderNumber")
    @Mapping(target = "bomName", source ="bom.bomName")
    @Mapping(target = "status", source ="workOrderStatus")
    WorkOrderListDTO toDTO(WorkOrder workOrder);
}
