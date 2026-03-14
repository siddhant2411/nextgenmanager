package com.nextgenmanager.nextgenmanager.items.dto;

import com.nextgenmanager.nextgenmanager.items.model.PriceType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.math.BigDecimal;
import java.util.Date;

/** Create / update request for ItemVendorPrice. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ItemVendorPriceRequestDTO {

    @NotNull
    private Integer inventoryItemId;

    @NotNull
    private Integer vendorId;

    @NotNull
    private PriceType priceType;

    @NotNull
    @DecimalMin(value = "0.0001", message = "Price must be greater than zero")
    private BigDecimal pricePerUnit;

    private String currency = "INR";

    private Integer leadTimeDays;

    private BigDecimal minimumOrderQuantity;

    private Date validFrom;
    private Date validTo;

    /** Setting true will clear the preferred flag on any existing preferred entry for this item+priceType. */
    private boolean isPreferredVendor = false;

    private boolean gstRegistered = true;

    private String paymentTerms;
    private String remarks;
}
