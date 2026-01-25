package com.nextgenmanager.nextgenmanager.production.mapper;

import com.nextgenmanager.nextgenmanager.assets.mapper.MachineDetailsResponseMapper;
import com.nextgenmanager.nextgenmanager.production.dto.ProductionJobResponseDTO;
import com.nextgenmanager.nextgenmanager.production.model.ProductionJob;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(
        componentModel = "spring"
)
public interface ProductionJobResponseMapper {


    @Mapping(target = "workCenter", source ="workCenter")
    @Mapping(target = "machineDetails", source = "machineDetails")
    @Mapping(target = "machineDetails.workCenter", source = "machineDetails.workCenter")
    ProductionJobResponseDTO toDTO(ProductionJob productionJob);
}
