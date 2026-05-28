package com.nextgenmanager.nextgenmanager.sales.dto;

import com.nextgenmanager.nextgenmanager.sales.model.SalesOrderApprovalStatus;
import com.nextgenmanager.nextgenmanager.sales.model.SalesOrderItem;
import com.nextgenmanager.nextgenmanager.sales.model.SalesOrderStatus;
import com.nextgenmanager.nextgenmanager.sales.model.TaxType;
import com.nextgenmanager.nextgenmanager.sales.model.VoucherType;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Date;
import java.util.List;

@Data
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class SalesOrderDto {

    private Long id;
    private String orderNumber;

    @Enumerated(EnumType.STRING)
    private VoucherType voucherType;                 // from VoucherType enum

    // Party & Reference
    private int customerId;
    private String customerName;                // convenience for UI
    private LocalDate orderDate;
    private Long quotationId;
    private String quotationNumber;             // convenience if needed

    // Status
    @Enumerated(EnumType.STRING)
    private SalesOrderStatus status;                       // from SalesOrderStatus enum

    // Line items
    private List<SalesOrderItemDto> items;

    // Commercial summary
    private BigDecimal subTotal;
    private BigDecimal discountPercentage;
    private BigDecimal discountAmount;
    private boolean includeFreightCharges;
    private BigDecimal freightAndForwardingCharges;
    private BigDecimal taxableValue;
    private TaxType taxType;

    private BigDecimal cgstAmount;
    private BigDecimal sgstAmount;
    private BigDecimal igstAmount;
    private BigDecimal cessAmount;
    private BigDecimal roundOffAmount;
    private BigDecimal netAmount;

    // Payment & Terms
    private String paymentTerms;
    private String incoterms;
    private String currency;

    // Logistics
    private String deliveryAddress;
    private String dispatchThrough;
    private String transportMode;
    private String ewayBillNumber;
    private LocalDate deliveryDate;
    private String packagingInstructions;
    private String shippingMethod;

    // References
    private String poNumber;
    private LocalDate poDate;
    private String reference;
    private String remarks;
    private String placeOfSupply;
    private String placeOfSupplyStateCode;
    private BigDecimal totalPayableAmount;

    // Approval workflow
    @Enumerated(EnumType.STRING)
    private SalesOrderApprovalStatus approvalStatus;
    private String approvedBy;
    private Date approvedDate;
    private String rejectionReason;
    private Date sentToCustomerAt;
    private String sentToCustomerEmail;
    private Integer revisionNo;
    private Long parentSoId;

    // Payment summary (computed from SalesPayment records)
    private BigDecimal totalAdvancePaid;
    private BigDecimal balanceDue;
}