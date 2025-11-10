package com.nextgenmanager.nextgenmanager.bom.mapper;

import com.nextgenmanager.nextgenmanager.bom.dto.BomDTO;
import com.nextgenmanager.nextgenmanager.bom.dto.BomPositionResponse;
import com.nextgenmanager.nextgenmanager.bom.model.Bom;
import com.nextgenmanager.nextgenmanager.bom.model.BomPosition;
import com.nextgenmanager.nextgenmanager.items.DTO.InventoryItemDTO;
import com.nextgenmanager.nextgenmanager.items.model.InventoryItem;

import java.util.stream.Collectors;

public class BomMapper {

    public static BomDTO toDto(Bom bom) {
        if (bom == null) return null;

        return BomDTO.builder()
                .id(bom.getId())
                .bomName(bom.getBomName())
                .parentInventoryItem(toDto(bom.getParentInventoryItem()))
                .childInventoryItems(
                        bom.getChildInventoryItems() != null ?
                                bom.getChildInventoryItems().stream()
                                        .map(BomMapper::toDto)
                                        .collect(Collectors.toList())
                                : null
                )
                .bomStatus(bom.getBomStatus() != null ? bom.getBomStatus().name() : null)
                .effectiveFrom(bom.getEffectiveFrom())
                .effectiveTo(bom.getEffectiveTo())
                .ecoNumber(bom.getEcoNumber())
                .changeReason(bom.getChangeReason())
                .approvedBy(bom.getApprovedBy())
                .approvalDate(bom.getApprovalDate())
                .approvalComments(bom.getApprovalComments())
                .description(bom.getDescription())
                .isActive(bom.getIsActive())
                .isDefault(bom.getIsDefault())
                .creationDate(bom.getCreationDate())
                .updatedDate(bom.getUpdatedDate())
                .deletedDate(bom.getDeletedDate())
                .build();
    }



    public static InventoryItemDTO toDto(InventoryItem item) {
        if (item == null) return null;

        return InventoryItemDTO.builder()
                .inventoryItemId(item.getInventoryItemId())
                .name(item.getName())
                .itemCode(item.getItemCode())
                .itemType(item.getItemType())
                .uom(item.getUom())
                .build();
    }

    public static BomPositionResponse toDto(BomPosition position) {
        if (position == null) return null;

        return BomPositionResponse.builder()
                .id(position.getId())
                .position(position.getPosition())
                .quantity(position.getQuantity())
                .childInventoryItem(toDto(position.getChildInventoryItem()))
                .build();
    }


}
