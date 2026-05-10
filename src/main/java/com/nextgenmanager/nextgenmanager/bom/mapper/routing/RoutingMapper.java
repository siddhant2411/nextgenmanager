package com.nextgenmanager.nextgenmanager.bom.mapper.routing;

import com.nextgenmanager.nextgenmanager.assets.mapper.MachineDetailsResponseMapper;
import com.nextgenmanager.nextgenmanager.bom.dto.routing.RoutingDto;
import com.nextgenmanager.nextgenmanager.bom.dto.routing.RoutingOperationDto;
import com.nextgenmanager.nextgenmanager.bom.model.routing.Routing;
import com.nextgenmanager.nextgenmanager.bom.model.routing.RoutingOperation;
import com.nextgenmanager.nextgenmanager.production.mapper.LaborRoleMapper;
import com.nextgenmanager.nextgenmanager.production.mapper.ProductionJobResponseMapper;
import com.nextgenmanager.nextgenmanager.production.mapper.WorkCenterResponseMapper;
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
