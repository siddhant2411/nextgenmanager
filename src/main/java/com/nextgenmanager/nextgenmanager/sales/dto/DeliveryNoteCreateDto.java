package com.nextgenmanager.nextgenmanager.sales.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DeliveryNoteCreateDto {
    private Long salesOrderId;
    private String deliveryNoteNo;
    private Date deliveryDate;
    private String lrNumber;
    private String transporter;
    private String vehicleNumber;
    private String ewayBillNumber;
    private String dispatchThrough;
    private String remarks;
    private List<DeliveryNoteItemDto> items;
}
