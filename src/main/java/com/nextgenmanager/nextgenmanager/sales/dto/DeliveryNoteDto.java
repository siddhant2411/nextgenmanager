package com.nextgenmanager.nextgenmanager.sales.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DeliveryNoteDto {
    private Long id;
    private Long salesOrderId;
    private String salesOrderNumber;
    private String customerName;
    private String deliveryNoteNo;
    private Date deliveryDate;
    private String lrNumber;
    private String transporter;
    private String vehicleNumber;
    private String ewayBillNumber;
    private String dispatchThrough;
    private String remarks;

    /** The pick this note shipped, when it came from one. Null for a direct dispatch. */
    private Long pickListId;
    private String pickNumber;

    private List<DeliveryNoteItemDetailDto> items;
}
