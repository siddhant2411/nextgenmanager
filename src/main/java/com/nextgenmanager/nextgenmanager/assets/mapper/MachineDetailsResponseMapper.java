package com.nextgenmanager.nextgenmanager.assets.mapper;

import com.nextgenmanager.nextgenmanager.assets.model.MachineDetails;
import com.nextgenmanager.nextgenmanager.assets.dto.MachineDetailsResponseDTO;
import org.mapstruct.Context;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface MachineDetailsResponseMapper {


    @Mapping(target = "workCenter", source ="workCenter")
    MachineDetailsResponseDTO toDTO(MachineDetails machineDetails);
}
