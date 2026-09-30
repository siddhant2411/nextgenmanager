package com.nextgenmanager.nextgenmanager.items.service;

import com.nextgenmanager.nextgenmanager.items.DTO.ItemRevisionDTO;
import com.nextgenmanager.nextgenmanager.items.model.ItemRevision;

public final class ItemRevisionMapper {

    private ItemRevisionMapper() {}

    public static ItemRevisionDTO toDto(ItemRevision r) {
        if (r == null) return null;
        return ItemRevisionDTO.builder()
                .id(r.getId())
                .inventoryItemId(r.getInventoryItem() != null ? r.getInventoryItem().getInventoryItemId() : 0)
                .revisionCode(r.getRevisionCode())
                .status(r.getStatus())
                .interchangeable(r.getInterchangeable())
                .ecoNumber(r.getEcoNumber())
                .changeReason(r.getChangeReason())
                .releasedBy(r.getReleasedBy())
                .releasedOn(r.getReleasedOn())
                .supersededById(r.getSupersededBy() != null ? r.getSupersededBy().getId() : null)
                .dimension(r.getDimension())
                .size(r.getSize())
                .weight(r.getWeight())
                .basicMaterial(r.getBasicMaterial())
                .processType(r.getProcessType())
                .drawingNumber(r.getDrawingNumber())
                .uom(r.getUom())
                .hsnCode(r.getHsnCode())
                .creationDate(r.getCreationDate())
                .updatedDate(r.getUpdatedDate())
                .build();
    }
}
