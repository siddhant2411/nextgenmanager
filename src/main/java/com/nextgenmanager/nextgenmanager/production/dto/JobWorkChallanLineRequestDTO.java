package com.nextgenmanager.nextgenmanager.production.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class JobWorkChallanLineRequestDTO {

    /** Inventory item ID. Null allowed if description is provided (customer-supplied material). */
    private Integer inventoryItemId;

    /** Free-text description — required if inventoryItemId is null. */
    private String description;

    /** HSN/SAC code. Auto-populated from item master if inventoryItemId is given. */
    private String hsnCode;

    @NotNull
    @DecimalMin(value = "0.0001", message = "Dispatched quantity must be greater than zero")
    private BigDecimal quantityDispatched;

    /** UOM. Copied from item master if not provided. */
    private String uom;

    /** Declared value per unit for GST challan (Section 19 valuation). */
    private BigDecimal valuePerUnit;

    private String remarks;
}
