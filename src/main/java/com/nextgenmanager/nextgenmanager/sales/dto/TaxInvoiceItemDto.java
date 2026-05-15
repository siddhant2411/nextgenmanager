package com.nextgenmanager.nextgenmanager.sales.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class TaxInvoiceItemDto {
    private Long id;
    private Integer inventoryItemId;
    private String inventoryItemName;
    private String description;
    private String hsnCode;
    private String serialNumbers;
    private BigDecimal qty;
    private BigDecimal pricePerUnit;
    private BigDecimal cgstAmount;
    private BigDecimal sgstAmount;
    private BigDecimal igstAmount;
    private BigDecimal totalAmount;
}
