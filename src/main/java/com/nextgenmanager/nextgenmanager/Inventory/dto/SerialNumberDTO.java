package com.nextgenmanager.nextgenmanager.Inventory.dto;

import lombok.Data;

import java.time.LocalDate;
import java.util.Date;

@Data
public class SerialNumberDTO {
    private Long id;
    private String serialNumber;
    private int inventoryItemId;
    private String itemCode;
    private String itemName;
    private Long batchId;
    private String batchNumber;
    private String status;
    private LocalDate receivedDate;
    private String source;
    private String sourceDocNo;
    private String warehouse;
    private Date consumedDate;
    private String consumedByDocNo;
    private String createdBy;
    private Date createdDate;
}
