package com.nextgenmanager.nextgenmanager.production.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.Date;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class WorkOrderMaterialReorderDTO {

    private Long id;
    private Long workOrderMaterialId;
    private String materialCode;
    private String materialName;

    private Long inventoryRequestId;
    private String referenceNumber;
    private String mrStatus;
    private BigDecimal mrApprovedQuantity;

    private BigDecimal requestedQuantity;
    private BigDecimal shortfallQuantity;
    private String remarks;

    private Date createdDate;
    private String createdBy;
}
