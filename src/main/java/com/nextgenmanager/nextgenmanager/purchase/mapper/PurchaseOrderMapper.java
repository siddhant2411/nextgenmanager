package com.nextgenmanager.nextgenmanager.purchase.mapper;

import com.nextgenmanager.nextgenmanager.purchase.dto.PurchaseOrderDto;
import com.nextgenmanager.nextgenmanager.purchase.dto.PurchaseOrderListDto;
import com.nextgenmanager.nextgenmanager.purchase.model.PurchaseOrder;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring", uses = {PurchaseOrderItemMapper.class})
public interface PurchaseOrderMapper {

    @Mapping(target = "vendorId",              source = "vendor.id")
    @Mapping(target = "vendorName",            source = "vendor.companyName")
    @Mapping(target = "vendorGstin",           source = "vendor.gstNumber")
    @Mapping(target = "vendorEmail",           source = "vendor.email")
    @Mapping(target = "vendorPhone",           source = "vendor.phone")
    @Mapping(target = "vendorBillingAddressId",source = "vendorBillingAddress.id")
    @Mapping(target = "shipToAddressId",       source = "shipToAddress.id")
    @Mapping(target = "salesOrderId",          source = "salesOrder.id")
    @Mapping(target = "grandTotalInWords",     ignore = true)   // populated by service
    PurchaseOrderDto toDto(PurchaseOrder entity);

    @Mapping(target = "vendorId",    source = "vendor.id")
    @Mapping(target = "vendorName",  source = "vendor.companyName")
    @Mapping(target = "itemCount",   expression = "java(entity.getItems() == null ? 0 : entity.getItems().size())")
    @Mapping(target = "daysOverdue", ignore = true)
    PurchaseOrderListDto toListDto(PurchaseOrder entity);
}
