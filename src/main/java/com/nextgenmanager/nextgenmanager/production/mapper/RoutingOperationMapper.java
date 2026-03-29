package com.nextgenmanager.nextgenmanager.production.mapper;

import com.nextgenmanager.nextgenmanager.assets.mapper.MachineDetailsResponseMapper;
import com.nextgenmanager.nextgenmanager.production.dto.RoutingOperationDto;
import com.nextgenmanager.nextgenmanager.production.model.RoutingOperation;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(
        componentModel = "spring",
        uses = {ProductionJobResponseMapper.class, WorkCenterResponseMapper.class,
                LaborRoleMapper.class, MachineDetailsResponseMapper.class,
                RoutingOperationDependencyMapper.class}
)
public interface RoutingOperationMapper {

    @Mapping(target = "workCenter",     source = "workCenter")
    @Mapping(target = "productionJob",  source = "productionJob")
    @Mapping(target = "laborRole",      source = "laborRole")
    @Mapping(target = "machineDetails", source = "machineDetails")
    @Mapping(target = "allowParallel",  source = "allowParallel")
    @Mapping(target = "parallelPath",   source = "parallelPath")
    @Mapping(target = "dependencies",   source = "dependencies")
    RoutingOperationDto toDTO(RoutingOperation routingOperation);

}
