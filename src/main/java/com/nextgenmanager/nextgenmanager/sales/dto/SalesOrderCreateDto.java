package com.nextgenmanager.nextgenmanager.sales.dto;

import com.nextgenmanager.nextgenmanager.sales.model.TaxType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class SalesOrderCreateDto {

    @Positive(message = "customerId is required")
    private int customerId;

    @NotNull(message = "orderDate is required")
    private LocalDate orderDate;

    private Long quotationId; // optional

    @Valid
    private List<SalesOrderItemDto> items;

    @Size(max = 20)
    private String currency;
    @Size(max = 200)
    private String paymentTerms;
    @Size(max = 200)
    private String incoterms;
    @Size(max = 50)
    private String poNumber;
    private LocalDate poDate;

    @DecimalMin(value = "0.0", inclusive = true, message = "discountPercentage must be >= 0")
    private BigDecimal discountPercentage;

    private TaxType taxType;

    @DecimalMin(value = "0.0", inclusive = true, message = "taxPercentage must be >= 0")
    private BigDecimal taxPercentage;

    private boolean includeFreightCharges;

    @DecimalMin(value = "0.0", inclusive = true, message = "freightAndForwardingCharges must be >= 0")
    private BigDecimal freightAndForwardingCharges;

    @Size(max = 500)
    private String deliveryAddress;
    @Size(max = 100)
    private String dispatchThrough;
    @Size(max = 50)
    private String transportMode;
    private LocalDate deliveryDate;
    @Size(max = 200)
    private String packagingInstructions;
    @Size(max = 200)
    private String remarks;
    @Size(max = 200)
    private String reference;
    @Size(max = 100)
    private String placeOfSupply;
    @Size(max = 5)
    private String placeOfSupplyStateCode;
}
