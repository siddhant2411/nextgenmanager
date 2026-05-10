package com.nextgenmanager.nextgenmanager.purchase.mapper;

import com.nextgenmanager.nextgenmanager.purchase.dto.PurchaseOrderItemDto;
import com.nextgenmanager.nextgenmanager.purchase.model.PurchaseOrderItem;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface PurchaseOrderItemMapper {

    @Mapping(target = "itemId",         source = "item.inventoryItemId")
    @Mapping(target = "itemCode",       source = "item.itemCode")
    @Mapping(target = "itemName",       source = "item.name")
    @Mapping(target = "salesOrderItemId", source = "salesOrderItem.id")
    @Mapping(target = "batchTracked",   source = "item.productInventorySettings.batchTracked")
    @Mapping(target = "serialTracked",  source = "item.productInventorySettings.serialTracked")
    PurchaseOrderItemDto toDto(PurchaseOrderItem entity);
}
