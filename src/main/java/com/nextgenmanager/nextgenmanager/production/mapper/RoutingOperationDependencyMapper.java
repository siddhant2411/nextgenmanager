package com.nextgenmanager.nextgenmanager.production.mapper;

import com.nextgenmanager.nextgenmanager.production.dto.RoutingOperationDependencyDTO;
import com.nextgenmanager.nextgenmanager.production.model.RoutingOperationDependency;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface RoutingOperationDependencyMapper {

    @Mapping(target = "dependsOnRoutingOperationId", source = "dependsOnRoutingOperation.id")
    @Mapping(target = "dependsOnSequenceNumber",     source = "dependsOnRoutingOperation.sequenceNumber")
    @Mapping(target = "dependsOnOperationName",      source = "dependsOnRoutingOperation.name")
    RoutingOperationDependencyDTO toDTO(RoutingOperationDependency dependency);
}
