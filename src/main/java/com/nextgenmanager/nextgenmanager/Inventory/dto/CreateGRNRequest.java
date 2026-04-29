package com.nextgenmanager.nextgenmanager.Inventory.dto;

import lombok.Data;
import java.time.LocalDate;
import java.util.List;

@Data
public class CreateGRNRequest {
    private Long purchaseOrderId;
    private Long vendorId;
    private LocalDate grnDate;
    private String warehouse;
    private String remarks;
    private String createdBy;
    private List<GRNLineItemDTO> items;
}
