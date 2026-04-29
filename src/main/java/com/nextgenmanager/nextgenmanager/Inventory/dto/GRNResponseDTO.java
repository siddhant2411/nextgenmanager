package com.nextgenmanager.nextgenmanager.Inventory.dto;

import lombok.Data;
import java.time.LocalDate;
import java.util.Date;
import java.util.List;

@Data
public class GRNResponseDTO {
    private Long id;
    private String grnNumber;
    private LocalDate grnDate;
    private Long purchaseOrderId;
    private String purchaseOrderNumber;
    private Long vendorId;
    private String vendorName;
    private String warehouse;
    private String status;
    private double totalAmount;
    private String remarks;
    private String createdBy;
    private Date createdDate;
    private List<GRNLineItemDTO> items;
}
