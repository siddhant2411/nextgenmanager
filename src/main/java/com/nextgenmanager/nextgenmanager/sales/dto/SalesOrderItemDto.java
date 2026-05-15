package com.nextgenmanager.nextgenmanager.sales.dto;

import com.nextgenmanager.nextgenmanager.items.model.InventoryItem;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.math.BigDecimal;

@Data
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class SalesOrderItemDto {

    private int id;

    @NotNull(message = "inventoryItem is required")
    private InventoryItem inventoryItem;

    private String inventoryItemName;

    @NotNull(message = "qty is required")
    @DecimalMin(value = "0.0", inclusive = false, message = "qty must be > 0")
    private BigDecimal qty;

    private String hsnCode;

    @NotNull(message = "pricePerUnit is required")
    @DecimalMin(value = "0.0", inclusive = true, message = "pricePerUnit must be >= 0")
    private BigDecimal pricePerUnit;

    @DecimalMin(value = "0.0", inclusive = true, message = "discountPercentage must be >= 0")
    private BigDecimal discountPercentage;

    @DecimalMin(value = "0.0", inclusive = true, message = "gstRatePct must be >= 0")
    private BigDecimal gstRatePct;

    private BigDecimal cgstAmount;
    private BigDecimal sgstAmount;
    private BigDecimal igstAmount;
    private BigDecimal unitPriceAfterDiscount;
    private BigDecimal totalAmountOfProduct;
    private BigDecimal dispatchedQty;
    private BigDecimal remainingQty;
}
