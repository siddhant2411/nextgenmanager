package com.nextgenmanager.nextgenmanager.production.mapper;

import com.nextgenmanager.nextgenmanager.assets.mapper.MachineDetailsResponseMapper;
import com.nextgenmanager.nextgenmanager.production.dto.RoutingDto;
import com.nextgenmanager.nextgenmanager.production.dto.RoutingOperationDto;
import com.nextgenmanager.nextgenmanager.production.model.Routing;
import com.nextgenmanager.nextgenmanager.production.model.RoutingOperation;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;
import java.util.stream.Collectors;

@Mapper(
        componentModel = "spring",
        uses = {ProductionJobResponseMapper.class, WorkCenterResponseMapper.class,
                LaborRoleMapper.class, MachineDetailsResponseMapper.class,
                RoutingOperationDependencyMapper.class}
)
public interface RoutingMapper {

    RoutingDto toDTO(Routing routing);

    @Mapping(target = "id",             source = "operation.id")
    @Mapping(target = "workCenter",     source = "operation.workCenter")
    @Mapping(target = "laborRole",      source = "operation.laborRole")
    @Mapping(target = "machineDetails", source = "operation.machineDetails")
    @Mapping(target = "allowParallel",  source = "operation.allowParallel")
    @Mapping(target = "parallelPath",   source = "operation.parallelPath")
    @Mapping(target = "dependencies",   source = "operation.dependencies")
    RoutingOperationDto mapOperation(RoutingOperation operation, Routing routing);

    default List<RoutingOperationDto> mapOperations(List<RoutingOperation> operations, Routing routing) {
        return operations.stream()
                .map(op -> mapOperation(op, routing))
                .collect(Collectors.toList());
    }
}
