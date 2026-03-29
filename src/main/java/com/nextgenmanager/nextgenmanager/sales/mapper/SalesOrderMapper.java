package com.nextgenmanager.nextgenmanager.sales.mapper;

import com.nextgenmanager.nextgenmanager.sales.dto.SalesOrderDto;
import com.nextgenmanager.nextgenmanager.sales.model.SalesOrder;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring", uses = {SalesOrderItemMapper.class})
public interface SalesOrderMapper {

    @Mapping(target = "customerId", source = "customer.id")
    @Mapping(target = "customerName", source = "customer.companyName")
    @Mapping(target = "quotationId", source = "quotation.id")
    @Mapping(target = "quotationNumber", source = "quotation.qtnNo")
    @Mapping(target = "items", source = "items")
    SalesOrderDto toDTO(SalesOrder entity);

    @Mapping(target = "customer", ignore = true)
    @Mapping(target = "quotation", ignore = true)
    @Mapping(target = "items", ignore = true)
    @Mapping(target = "creationDate", ignore = true)
    @Mapping(target = "updatedDate", ignore = true)
    @Mapping(target = "deletedDate", ignore = true)
    @Mapping(target = "deliveryNotes", ignore = true)
    @Mapping(target = "taxInvoices", ignore = true)
    SalesOrder toEntity(SalesOrderDto dto);
}
