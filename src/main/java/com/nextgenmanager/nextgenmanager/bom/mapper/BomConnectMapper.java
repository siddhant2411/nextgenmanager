package com.nextgenmanager.nextgenmanager.bom.mapper;

import com.nextgenmanager.nextgenmanager.bom.dto.BomConnectDTO;
import com.nextgenmanager.nextgenmanager.bom.model.Bom;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface BomConnectMapper {


//    @Mapping(target = "positions", source = "bom.positions")
    BomConnectDTO toDTO(Bom bom);
}
