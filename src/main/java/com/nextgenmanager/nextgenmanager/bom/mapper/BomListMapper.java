package com.nextgenmanager.nextgenmanager.bom.mapper;

import com.nextgenmanager.nextgenmanager.bom.dto.BomListDTO;
import com.nextgenmanager.nextgenmanager.bom.model.Bom;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface BomListMapper {

    @Mapping(target = "parentItemCode", source = "parentInventoryItem.itemCode")
    @Mapping(target = "parentItemName", source = "parentInventoryItem.name")
    @Mapping(target = "parentDrawingNumber", source = "parentInventoryItem.productSpecification.drawingNumber")
    @Mapping(target = "uom", source = "parentInventoryItem.uom")
    BomListDTO toDTO(Bom bom);
}
