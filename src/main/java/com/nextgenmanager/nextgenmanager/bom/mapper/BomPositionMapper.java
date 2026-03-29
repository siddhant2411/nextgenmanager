package com.nextgenmanager.nextgenmanager.bom.mapper;

import com.nextgenmanager.nextgenmanager.bom.dto.BomConnectDTO;
import com.nextgenmanager.nextgenmanager.bom.dto.BomPositionDTO;
import com.nextgenmanager.nextgenmanager.bom.model.Bom;
import com.nextgenmanager.nextgenmanager.bom.model.BomPosition;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface BomPositionMapper {

    @Mapping(target = "positionId", source = "id")
    @Mapping(target = "childBomId", source = "childBom.id")
    @Mapping(target = "bomName", source = "parentBom.bomName")
    @Mapping(target = "parentItemName", source = "parentBom.parentInventoryItem.name")
    @Mapping(target = "parentItemCode", source = "parentBom.parentInventoryItem.itemCode")
    @Mapping(target = "parentDrawingNumber", source = "parentBom.parentInventoryItem.productSpecification.drawingNumber")
    @Mapping(target = "revision", source = "parentBom.parentInventoryItem.revision")
    @Mapping(target = "uom", source = "parentBom.parentInventoryItem.uom")
    @Mapping(target = "routingOperationId", source = "routingOperation.id")
    @Mapping(target = "routingOperationName", source = "routingOperation.name")
    @Mapping(target = "hasChildBom", ignore = true)
    BomPositionDTO toDTO(BomPosition position);
}