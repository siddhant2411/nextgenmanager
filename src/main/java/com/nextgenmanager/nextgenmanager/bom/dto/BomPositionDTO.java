package com.nextgenmanager.nextgenmanager.bom.dto;


import com.nextgenmanager.nextgenmanager.items.model.UOM;
import lombok.*;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class BomPositionDTO {

    private int id;
    private String bomName;
    private String parentItemName;
    private String parentItemCode;
    private String parentDrawingNumber;
    private String revision;
    private UOM uom;
    private int position;
    private double quantity;
    private boolean hasChildBom;


}
