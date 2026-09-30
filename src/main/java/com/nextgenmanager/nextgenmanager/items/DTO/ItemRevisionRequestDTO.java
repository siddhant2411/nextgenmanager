package com.nextgenmanager.nextgenmanager.items.DTO;

import com.nextgenmanager.nextgenmanager.items.model.UOM;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Body for the item-revision endpoints:
 * - revise: only {@code changeReason} / {@code ecoNumber} are read.
 * - update draft: the engineering fields are read; {@code changeReason}/{@code ecoNumber} optional.
 * - release: {@code interchangeable}, {@code changeReason}, {@code ecoNumber}, {@code approvalComments} are read.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ItemRevisionRequestDTO {
    private String changeReason;
    private String ecoNumber;
    private String approvalComments;
    private Boolean interchangeable;

    private String dimension;
    private String size;
    private String weight;
    private String basicMaterial;
    private String processType;
    private String drawingNumber;
    private UOM uom;
    private String hsnCode;
}
