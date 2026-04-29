package com.nextgenmanager.nextgenmanager.bom.mapper;

import com.nextgenmanager.nextgenmanager.bom.dto.BomDTO;
import com.nextgenmanager.nextgenmanager.bom.dto.BomPositionDTO;
import com.nextgenmanager.nextgenmanager.bom.dto.BomPositionResponse;
import com.nextgenmanager.nextgenmanager.bom.model.Bom;
import com.nextgenmanager.nextgenmanager.bom.model.BomPosition;
import com.nextgenmanager.nextgenmanager.items.DTO.InventoryItemDTO;
import com.nextgenmanager.nextgenmanager.items.model.InventoryItem;

import java.util.Collections;
import java.util.Map;
import java.util.stream.Collectors;

public class BomMapper {

    public static BomDTO toDto(Bom bom) {
        return toDto(bom, Collections.emptyMap());
    }

    /**
     * @param activeBomMap maps childInventoryItemId -> activeBomId (null entry = no active BOM)
     */
    public static BomDTO toDto(Bom bom, Map<Integer, Integer> activeBomMap) {
        if (bom == null) return null;

        return BomDTO.builder()
                .id(bom.getId())
                .bomName(bom.getBomName())
                .parentInventoryItem(toDto(bom.getParentInventoryItem()))
                .positions(
                        bom.getPositions() != null ?
                                bom.getPositions().stream()
                                        .map(pos -> toPositionDto(pos, activeBomMap))
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


    public static BomPositionDTO toPositionDto(BomPosition position, Map<Integer, Integer> activeBomMap) {
        if (position.getChildInventoryItem() == null) return null;

        InventoryItem item = position.getChildInventoryItem();
        Integer activeBomId = activeBomMap.get(item.getInventoryItemId());
        boolean hasActiveBom = activeBomId != null;

        return BomPositionDTO.builder()
                .positionId(position.getId())
                .childInventoryItemId(item.getInventoryItemId())
                .itemName(item.getName())
                .itemCode(item.getItemCode())
                .drawingNumber(item.getProductSpecification() != null
                        ? item.getProductSpecification().getDrawingNumber() : null)
                .uom(item.getUom())
                .position(position.getPosition())
                .quantity(position.getQuantity())
                .scrapPercentage(position.getScrapPercentage())
                .hasActiveBom(hasActiveBom)
                .activeBomId(activeBomId)
                .routingOperationId(position.getRoutingOperation() != null
                        ? position.getRoutingOperation().getId() : null)
                .routingOperationName(position.getRoutingOperation() != null
                        ? position.getRoutingOperation().getName() : null)
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
                .purchased(item.getProductInventorySettings() != null && item.getProductInventorySettings().isPurchased())
                .manufactured(item.getProductInventorySettings() != null && item.getProductInventorySettings().isManufactured())
                .build();
    }

    public static BomPositionResponse toDto(BomPosition position) {
        if (position == null) return null;

        return BomPositionResponse.builder()
                .id(position.getId())
                .position(position.getPosition())
                .quantity(position.getQuantity())
                .childInventoryItem(position.getChildInventoryItem())
                .build();
    }


}
