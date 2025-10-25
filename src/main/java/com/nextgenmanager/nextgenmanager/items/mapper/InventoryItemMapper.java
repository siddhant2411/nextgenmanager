package com.nextgenmanager.nextgenmanager.items.mapper;

import com.nextgenmanager.nextgenmanager.items.DTO.InventoryItemDTO;
import com.nextgenmanager.nextgenmanager.items.model.InventoryItem;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface InventoryItemMapper {
    @Mapping(target = "dimension", source = "productSpecification.dimension")
    @Mapping(target = "size", source = "productSpecification.size")
    @Mapping(target = "weight", source = "productSpecification.weight")
    @Mapping(target = "basicMaterial", source = "productSpecification.basicMaterial")
    @Mapping(target = "drawingNumber", source = "productSpecification.drawingNumber")
    @Mapping(target = "availableQuantity", source = "productInventorySettings.availableQuantity")
    @Mapping(target = "sellingPrice", source = "productFinanceSettings.sellingPrice")
    InventoryItemDTO toDTO(InventoryItem item);
}