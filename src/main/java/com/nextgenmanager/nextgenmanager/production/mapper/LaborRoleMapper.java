package com.nextgenmanager.nextgenmanager.production.mapper;

import com.nextgenmanager.nextgenmanager.production.dto.LaborRoleResponseDTO;
import com.nextgenmanager.nextgenmanager.production.model.LaborRole;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface LaborRoleMapper {
    LaborRoleResponseDTO toDTO(LaborRole laborRole);
}
