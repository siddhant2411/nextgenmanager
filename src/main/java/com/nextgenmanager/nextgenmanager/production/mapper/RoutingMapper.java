package com.nextgenmanager.nextgenmanager.production.mapper;

import com.nextgenmanager.nextgenmanager.production.dto.RoutingDto;
import com.nextgenmanager.nextgenmanager.production.dto.RoutingOperationDto;
import com.nextgenmanager.nextgenmanager.production.model.Routing;
import com.nextgenmanager.nextgenmanager.production.model.RoutingOperation;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collector;
import java.util.stream.Collectors;

@Mapper(componentModel = "spring")
public interface RoutingMapper {

    RoutingDto toDTO(Routing routing);

    @Mapping(target = "id", source = "operation.id") // FIX: specify the id source
    @Mapping(target = "workCenter", source = "operation.workCenter")
    RoutingOperationDto mapOperation(RoutingOperation operation, Routing routing);

    default List<RoutingOperationDto> mapOperations(List<RoutingOperation> operations, Routing routing) {
        return operations.stream()
                .map(op -> mapOperation(op, routing))
                .collect(Collectors.toList());
    }
}