package com.nextgenmanager.nextgenmanager.production.mapper;

import com.nextgenmanager.nextgenmanager.production.dto.WorkCenterResponseDTO;
import com.nextgenmanager.nextgenmanager.production.model.workCenter.WorkCenter;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface WorkCenterResponseMapper {

    WorkCenterResponseDTO toDTO(WorkCenter workCenter);
}
