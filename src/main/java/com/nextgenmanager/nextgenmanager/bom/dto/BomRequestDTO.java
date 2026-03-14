package com.nextgenmanager.nextgenmanager.bom.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.nextgenmanager.nextgenmanager.bom.model.Bom;
import com.nextgenmanager.nextgenmanager.bom.model.BomPosition;
import com.nextgenmanager.nextgenmanager.bom.model.BomStatus;
import com.nextgenmanager.nextgenmanager.items.model.InventoryItem;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class BomRequestDTO {

    private Integer id;
    private String bomName;
    private InventoryItem parentInventoryItem;
    private List<BomPositionRequestDTO> positions;
    private BomStatus bomStatus;
    private Date effectiveFrom;
    private Date effectiveTo;
    private String revision;
    private String description;
    private Boolean isActive;
    private Boolean isDefault;

    public Bom toEntity() {
        Bom entity = new Bom();
        if (id != null) {
            entity.setId(id);
        }

        entity.setBomName(bomName);
        entity.setParentInventoryItem(parentInventoryItem);
        entity.setBomStatus(bomStatus);
        entity.setEffectiveFrom(effectiveFrom);
        entity.setEffectiveTo(effectiveTo);
        entity.setRevision(revision);
        entity.setDescription(description);
        entity.setIsActive(isActive);
        entity.setIsDefault(isDefault);

        if (positions != null) {
            List<BomPosition> mappedPositions = positions.stream()
                    .map(BomPositionRequestDTO::toEntity)
                    .collect(Collectors.toList());
            mappedPositions.forEach(position -> position.setParentBom(entity));
            entity.setPositions(mappedPositions);
        }

        return entity;
    }
}
