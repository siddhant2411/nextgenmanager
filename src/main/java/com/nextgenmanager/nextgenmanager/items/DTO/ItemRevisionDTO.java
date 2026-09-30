package com.nextgenmanager.nextgenmanager.items.DTO;

import com.nextgenmanager.nextgenmanager.items.model.ItemRevisionStatus;
import com.nextgenmanager.nextgenmanager.items.model.UOM;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ItemRevisionDTO {
    private Long id;
    private int inventoryItemId;
    private String revisionCode;
    private ItemRevisionStatus status;
    private Boolean interchangeable;
    private String ecoNumber;
    private String changeReason;
    private String releasedBy;
    private Date releasedOn;
    private Long supersededById;

    private String dimension;
    private String size;
    private String weight;
    private String basicMaterial;
    private String processType;
    private String drawingNumber;
    private UOM uom;
    private String hsnCode;

    private Date creationDate;
    private Date updatedDate;
}
