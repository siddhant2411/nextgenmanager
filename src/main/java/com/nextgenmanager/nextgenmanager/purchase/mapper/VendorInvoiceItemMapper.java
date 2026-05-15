package com.nextgenmanager.nextgenmanager.purchase.mapper;

import com.nextgenmanager.nextgenmanager.purchase.dto.VendorInvoiceItemDto;
import com.nextgenmanager.nextgenmanager.purchase.model.VendorInvoiceItem;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface VendorInvoiceItemMapper {

    @Mapping(target = "itemId",   source = "item.inventoryItemId")
    @Mapping(target = "itemCode", source = "item.itemCode")
    @Mapping(target = "itemName", source = "item.name")
    VendorInvoiceItemDto toDto(VendorInvoiceItem entity);
}
