package com.nextgenmanager.nextgenmanager.bom.mapper;

import com.nextgenmanager.nextgenmanager.bom.dto.BomDTO;
import com.nextgenmanager.nextgenmanager.bom.dto.BomListDTO;
import com.nextgenmanager.nextgenmanager.bom.dto.BomPositionDTO;
import com.nextgenmanager.nextgenmanager.bom.dto.BomPositionResponse;
import com.nextgenmanager.nextgenmanager.bom.model.Bom;
import com.nextgenmanager.nextgenmanager.bom.model.BomPosition;
import com.nextgenmanager.nextgenmanager.items.DTO.InventoryItemDTO;
import com.nextgenmanager.nextgenmanager.items.model.InventoryItem;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.Collections;
import java.util.stream.Collectors;

public class BomMapper {

    public static BomDTO toDto(Bom bom) {
        if (bom == null) return null;

        return BomDTO.builder()
                .id(bom.getId())
                .bomName(bom.getBomName())
                .parentInventoryItem(toDto(bom.getParentInventoryItem()))
                .positions(
                        bom.getPositions() != null ?
                                bom.getPositions().stream()
                                        .map(pos -> toChildBomDto(pos)

                                        )
                                        .collect(Collectors.toList())
                                : Collections.emptyList()
                )
                .bomStatus(bom.getBomStatus() != null ? bom.getBomStatus().name() : null)
                .revision(bom.getRevision())
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


    public static BomPositionDTO toChildBomDto(BomPosition position) {
        if (position.getChildBom() == null) return null;

        Bom child = position.getChildBom();
        InventoryItem item = child.getParentInventoryItem();

        return BomPositionDTO.builder()
                .id(child.getId())
                .bomName(child.getBomName())
                .revision(child.getRevision())
                .parentDrawingNumber(item!=null ? (item.getProductSpecification()!=null?
                        item.getProductSpecification().getDrawingNumber():null):null)
                .parentItemCode(item != null ? item.getItemCode() : null)
                .parentItemName(item != null ? item.getName() : null)
                .uom(item != null ? item.getUom() : null)
                .position(position.getPosition())
                .quantity(position.getQuantity())
                .scrapPercentage(position.getScrapPercentage())
                .hasChildBom(!position.getChildBom().getPositions().isEmpty())
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
                .childBom(position.getChildBom())
                .build();
    }


}
