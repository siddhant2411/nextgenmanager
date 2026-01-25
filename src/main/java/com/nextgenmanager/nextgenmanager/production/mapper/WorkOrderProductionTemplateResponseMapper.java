package com.nextgenmanager.nextgenmanager.production.mapper;

import com.nextgenmanager.nextgenmanager.production.dto.WorkOrderProductionTemplateResponseDTO;
import com.nextgenmanager.nextgenmanager.production.model.WorkOrderProductionTemplate;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface WorkOrderProductionTemplateResponseMapper {


    @Mapping(target = "workOrderJobLists", source = "workOrderJobLists")
    @Mapping(target = "defaultWorkCenter", source = "defaultWorkCenter")
    WorkOrderProductionTemplateResponseDTO toDTO(WorkOrderProductionTemplate workOrderProductionTemplate);
}
