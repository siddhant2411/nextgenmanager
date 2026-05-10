package com.nextgenmanager.nextgenmanager.purchase.requisition.mapper;

import com.nextgenmanager.nextgenmanager.purchase.requisition.dto.PurchaseRequisitionDto;
import com.nextgenmanager.nextgenmanager.purchase.requisition.dto.PurchaseRequisitionListDto;
import com.nextgenmanager.nextgenmanager.purchase.requisition.model.PurchaseRequisition;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring", uses = {PurchaseRequisitionItemMapper.class})
public interface PurchaseRequisitionMapper {

    @Mapping(target = "workCenterId",   expression = "java(entity.getWorkCenter() == null ? null : entity.getWorkCenter().getId())")
    @Mapping(target = "workCenterCode", expression = "java(entity.getWorkCenter() == null ? null : entity.getWorkCenter().getCenterCode())")
    @Mapping(target = "workCenterName", expression = "java(entity.getWorkCenter() == null ? null : entity.getWorkCenter().getCenterName())")
    PurchaseRequisitionDto toDto(PurchaseRequisition entity);

    @Mapping(target = "itemCount",
            expression = "java(entity.getItems() == null ? 0 : entity.getItems().size())")
    PurchaseRequisitionListDto toListDto(PurchaseRequisition entity);
}
