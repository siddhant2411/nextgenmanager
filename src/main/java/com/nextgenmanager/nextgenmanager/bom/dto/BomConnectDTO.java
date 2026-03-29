package com.nextgenmanager.nextgenmanager.bom.dto;

import com.nextgenmanager.nextgenmanager.bom.model.BomStatus;
import com.nextgenmanager.nextgenmanager.items.DTO.InventoryItemDTO;
import lombok.*;

import java.util.Date;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BomConnectDTO {

    private int id;
    private String bomName;
    private InventoryItemDTO parentInventoryItem;
    private BomStatus bomStatus;
    private String revision;
    private Date effectiveFrom;
    private Date effectiveTo;
    private String description;
    private Boolean isActive;
    private Boolean isDefault;
    private List<BomPositionDTO> positions;
}
