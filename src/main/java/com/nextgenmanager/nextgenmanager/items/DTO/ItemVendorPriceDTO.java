package com.nextgenmanager.nextgenmanager.items.dto;

import com.nextgenmanager.nextgenmanager.items.model.PriceType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.Date;

/** Read-only response DTO for ItemVendorPrice. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ItemVendorPriceDTO {

    private Long id;

    private int inventoryItemId;
    private String itemCode;
    private String itemName;

    private int vendorId;
    private String vendorName;
    private String vendorGstNumber;

    private PriceType priceType;
    private BigDecimal pricePerUnit;
    private String currency;

    private Integer leadTimeDays;
    private BigDecimal minimumOrderQuantity;

    private Date validFrom;
    private Date validTo;

    private boolean isPreferredVendor;
    private boolean gstRegistered;
    private String paymentTerms;
    private String remarks;

    private Date lastQuotedDate;
    private Date creationDate;
    private Date updatedDate;
}
