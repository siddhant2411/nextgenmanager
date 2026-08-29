package com.nextgenmanager.nextgenmanager.sales.dto;

import com.nextgenmanager.nextgenmanager.sales.model.SalesOrderApprovalStatus;
import com.nextgenmanager.nextgenmanager.sales.model.SalesOrderStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Date;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SalesOrderDisplayDto {

    private Long id;
    private String orderNumber;
    private LocalDate orderDate;
    private String customerName;
    private String poNumber;
    private BigDecimal netAmount;
    private SalesOrderStatus status;
    private SalesOrderApprovalStatus approvalStatus;
    private String dispatchThrough;
    private LocalDate deliveryDate;
    private Date updatedDate;
    private String currency;
    private String phone;
    private String email;

    // Where the order came from. Without these the list cannot answer "which enquiry produced
    // this order?" without a round trip per row, and an import has no exact key to re-run against.
    private Long enquiryId;
    private String enquiryNumber;
    private Long quotationId;
    private String quotationNumber;
    private String reference;
}
