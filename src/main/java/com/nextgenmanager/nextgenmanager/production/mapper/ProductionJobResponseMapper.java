package com.nextgenmanager.nextgenmanager.production.mapper;

import com.nextgenmanager.nextgenmanager.production.dto.ProductionJobResponseDTO;
import com.nextgenmanager.nextgenmanager.production.model.ProductionJob;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface ProductionJobResponseMapper {

    ProductionJobResponseDTO toDTO(ProductionJob productionJob);
}
