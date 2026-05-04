package com.nextgenmanager.nextgenmanager.production.mapper;

import com.nextgenmanager.nextgenmanager.production.dto.WorkOrderQaEntryDTO;
import com.nextgenmanager.nextgenmanager.production.model.WorkOrderQaEntry;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface WorkOrderQaEntryMapper {

    @Mapping(target = "workOrderOperationId", source = "workOrderOperation.id")
    WorkOrderQaEntryDTO toDTO(WorkOrderQaEntry entry);
}
