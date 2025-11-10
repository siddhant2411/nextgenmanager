package com.nextgenmanager.nextgenmanager.bom.dto;

import com.nextgenmanager.nextgenmanager.items.DTO.InventoryItemDTO;
import lombok.*;

import java.util.Date;
import java.util.List;


@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BomDTO {
    private int id;
    private String bomName;
    private InventoryItemDTO parentInventoryItem;
    private List<BomPositionResponse> childInventoryItems;
    private String bomStatus;
    private Date effectiveFrom;
    private Date effectiveTo;
    private String ecoNumber;
    private String changeReason;
    private String approvedBy;
    private Date approvalDate;
    private String approvalComments;
    private String description;
    private Boolean isActive;
    private Boolean isDefault;
    private Date creationDate;
    private Date updatedDate;
    private Date deletedDate;

}