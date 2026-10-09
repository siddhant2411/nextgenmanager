package com.nextgenmanager.nextgenmanager.production.mapper;

import com.nextgenmanager.nextgenmanager.production.dto.WorkCenterResponseDTO;
import com.nextgenmanager.nextgenmanager.production.model.workCenter.WorkCenter;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface WorkCenterResponseMapper {

    @Mapping(source = "warehouse.id", target = "warehouseId")
    @Mapping(source = "warehouse.code", target = "warehouseCode")
    @Mapping(source = "warehouse.name", target = "warehouseName")
    WorkCenterResponseDTO toDTO(WorkCenter workCenter);
}
