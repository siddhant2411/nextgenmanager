package com.nextgenmanager.nextgenmanager.Inventory.dto;

import lombok.Data;

import java.time.LocalDate;
import java.util.Date;

@Data
public class BatchNumberDTO {
    private Long id;
    private String batchNumber;
    private int inventoryItemId;
    private String itemCode;
    private String itemName;
    private LocalDate manufacturingDate;
    private LocalDate expiryDate;
    private String supplierBatchNo;
    private double originalQty;
    private double remainingQty;
    private String status;
    private String source;
    private String sourceDocNo;
    private String warehouse;
    private String createdBy;
    private Date createdDate;
}
