package com.nextgenmanager.nextgenmanager.purchase.requisition.mapper;

import com.nextgenmanager.nextgenmanager.purchase.requisition.dto.PurchaseRequisitionItemDto;
import com.nextgenmanager.nextgenmanager.purchase.requisition.model.PurchaseRequisitionItem;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface PurchaseRequisitionItemMapper {

    @Mapping(target = "itemId",              source = "item.inventoryItemId")
    @Mapping(target = "itemCode",            source = "item.itemCode")
    @Mapping(target = "itemName",            source = "item.name")
    @Mapping(target = "suggestedVendorId",   source = "suggestedVendor.id")
    @Mapping(target = "suggestedVendorName", source = "suggestedVendor.companyName")
    PurchaseRequisitionItemDto toDto(PurchaseRequisitionItem entity);
}
