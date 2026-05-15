package com.nextgenmanager.nextgenmanager.Inventory.dto;

import com.nextgenmanager.nextgenmanager.Inventory.model.QualityStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Date;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AvailableInstanceDetailDto {
    private Long instanceId;
    private int inventoryItemId;
    private String itemCode;
    private String itemName;
    private BigDecimal quantity;
    private BigDecimal costPerUnit;
    private String batchNumber;
    private LocalDate manufacturingDate;
    private LocalDate expiryDate;
    private String serialNumber;
    private QualityStatus qualityStatus;
    private String qualityRemarks;
    private String status;
}
