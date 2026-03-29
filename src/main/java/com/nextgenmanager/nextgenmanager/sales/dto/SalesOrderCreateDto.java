package com.nextgenmanager.nextgenmanager.sales.dto;

import com.nextgenmanager.nextgenmanager.sales.model.TaxType;
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
public class SalesOrderCreateDto {

    private int customerId;
    private LocalDate orderDate;
    private Long quotationId; // optional
    private List<SalesOrderItemDto> items;
    private String currency;
    private String paymentTerms;
    private String incoterms;
    private String poNumber;
    private LocalDate poDate;

    private BigDecimal discountPercentage;
    private TaxType taxType;
    private BigDecimal taxPercentage;

    private boolean includeFreightCharges;
    private BigDecimal freightAndForwardingCharges;


    private String deliveryAddress;
    private String dispatchThrough;
    private String transportMode;
    private LocalDate deliveryDate;
    private String packagingInstructions;
    private String remarks;
    private String reference;



}
