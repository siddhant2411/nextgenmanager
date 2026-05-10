package com.nextgenmanager.nextgenmanager.bom.mapper.routing;

import com.nextgenmanager.nextgenmanager.assets.mapper.MachineDetailsResponseMapper;
import com.nextgenmanager.nextgenmanager.bom.dto.routing.RoutingOperationDto;
import com.nextgenmanager.nextgenmanager.bom.model.routing.RoutingOperation;
import com.nextgenmanager.nextgenmanager.production.mapper.LaborRoleMapper;
import com.nextgenmanager.nextgenmanager.production.mapper.ProductionJobResponseMapper;
import com.nextgenmanager.nextgenmanager.production.mapper.WorkCenterResponseMapper;
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
