package com.nextgenmanager.nextgenmanager.purchase.mapper;

import com.nextgenmanager.nextgenmanager.purchase.dto.VendorInvoiceDto;
import com.nextgenmanager.nextgenmanager.purchase.dto.VendorInvoiceListDto;
import com.nextgenmanager.nextgenmanager.purchase.model.VendorInvoice;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring", uses = {VendorInvoiceItemMapper.class})
public interface VendorInvoiceMapper {

    @Mapping(target = "poId",               source = "purchaseOrder.id")
    @Mapping(target = "purchaseOrderNumber", source = "purchaseOrder.purchaseOrderNumber")
    @Mapping(target = "vendorId",           source = "vendor.id")
    @Mapping(target = "vendorName",         source = "vendor.companyName")
    @Mapping(target = "grnId",              source = "grn.id")
    @Mapping(target = "grnNumber",          source = "grn.grnNumber")
    VendorInvoiceDto toDto(VendorInvoice entity);

    @Mapping(target = "poId",               source = "purchaseOrder.id")
    @Mapping(target = "purchaseOrderNumber", source = "purchaseOrder.purchaseOrderNumber")
    @Mapping(target = "vendorId",           source = "vendor.id")
    @Mapping(target = "vendorName",         source = "vendor.companyName")
    VendorInvoiceListDto toListDto(VendorInvoice entity);
}
