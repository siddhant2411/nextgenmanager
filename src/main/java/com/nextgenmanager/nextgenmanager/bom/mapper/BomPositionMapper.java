package com.nextgenmanager.nextgenmanager.bom.mapper;

import com.nextgenmanager.nextgenmanager.bom.dto.BomConnectDTO;
import com.nextgenmanager.nextgenmanager.bom.dto.BomPositionDTO;
import com.nextgenmanager.nextgenmanager.bom.model.Bom;
import com.nextgenmanager.nextgenmanager.bom.model.BomPosition;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface BomPositionMapper {

    @Mapping(target = "bomName", source = "parentBom.bomName")
    @Mapping(target = "parentItemName", source = "parentBom.parentInventoryItem.name")
    @Mapping(target = "parentItemCode", source = "parentBom.parentInventoryItem.itemCode")
    @Mapping(target = "parentDrawingNumber", source = "parentBom.parentInventoryItem.productSpecification.drawingNumber")
    @Mapping(target = "revision", source = "parentBom.parentInventoryItem.revision")
    @Mapping(target = "uom", source = "parentBom.parentInventoryItem.uom")
    BomPositionDTO toDTO(BomPosition position);
}