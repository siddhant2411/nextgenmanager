package com.nextgenmanager.nextgenmanager.bom.mapper.routing;

import com.nextgenmanager.nextgenmanager.bom.dto.routing.RoutingOperationDependencyDTO;
import com.nextgenmanager.nextgenmanager.bom.model.routing.RoutingOperationDependency;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface RoutingOperationDependencyMapper {

    @Mapping(target = "dependsOnRoutingOperationId", source = "dependsOnRoutingOperation.id")
    @Mapping(target = "dependsOnSequenceNumber",     source = "dependsOnRoutingOperation.sequenceNumber")
    @Mapping(target = "dependsOnOperationName",      source = "dependsOnRoutingOperation.name")
    RoutingOperationDependencyDTO toDTO(RoutingOperationDependency dependency);
}
