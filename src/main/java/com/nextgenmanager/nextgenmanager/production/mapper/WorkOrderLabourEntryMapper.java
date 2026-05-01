package com.nextgenmanager.nextgenmanager.production.mapper;

import com.nextgenmanager.nextgenmanager.production.dto.WorkOrderLabourEntryDTO;
import com.nextgenmanager.nextgenmanager.production.model.WorkOrderLabourEntry;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring", uses = {LaborRoleMapper.class})
public interface WorkOrderLabourEntryMapper {

    @Mapping(target = "workOrderOperationId", source = "workOrderOperation.id")
    WorkOrderLabourEntryDTO toDTO(WorkOrderLabourEntry entry);
}
