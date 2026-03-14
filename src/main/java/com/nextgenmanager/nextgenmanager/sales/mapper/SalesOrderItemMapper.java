package com.nextgenmanager.nextgenmanager.sales.mapper;

import com.nextgenmanager.nextgenmanager.sales.dto.SalesOrderItemDto;
import com.nextgenmanager.nextgenmanager.sales.model.SalesOrderItem;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface SalesOrderItemMapper {

    @Mapping(target = "inventoryItemName", source = "inventoryItem.name")
    @Mapping(target = "id", source = "id")
    SalesOrderItemDto toDTO(SalesOrderItem entity);

    @Mapping(target = "inventoryItem", source = "inventoryItem")
    @Mapping(target = "salesOrder", ignore = true)
    @Mapping(target = "inventoryInstanceList", ignore = true)
    SalesOrderItem toEntity(SalesOrderItemDto dto);
}
