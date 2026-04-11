package com.nextgenmanager.nextgenmanager.bom.mapper;

import com.nextgenmanager.nextgenmanager.bom.dto.BomPositionDTO;
import com.nextgenmanager.nextgenmanager.bom.model.BomPosition;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface BomPositionMapper {

    @Mapping(target = "positionId", source = "id")
    @Mapping(target = "childInventoryItemId", source = "childInventoryItem.inventoryItemId")
    @Mapping(target = "itemName", source = "childInventoryItem.name")
    @Mapping(target = "itemCode", source = "childInventoryItem.itemCode")
    @Mapping(target = "drawingNumber", source = "childInventoryItem.productSpecification.drawingNumber")
    @Mapping(target = "uom", source = "childInventoryItem.uom")
    @Mapping(target = "routingOperationId", source = "routingOperation.id")
    @Mapping(target = "routingOperationName", source = "routingOperation.name")
    @Mapping(target = "hasActiveBom", ignore = true)
    @Mapping(target = "activeBomId", ignore = true)
    BomPositionDTO toDTO(BomPosition position);
}
