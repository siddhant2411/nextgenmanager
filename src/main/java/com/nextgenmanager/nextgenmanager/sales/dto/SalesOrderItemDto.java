package com.nextgenmanager.nextgenmanager.sales.dto;

import com.nextgenmanager.nextgenmanager.items.model.InventoryItem;
import lombok.*;

import java.math.BigDecimal;

@Data
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class SalesOrderItemDto {

    private int id;                // for existing items
    private InventoryItem inventoryItem;
    private String inventoryItemName;  // convenience
    private BigDecimal qty;
    private String hsnCode;
    private BigDecimal pricePerUnit;
    private BigDecimal discountPercentage;
    private BigDecimal unitPriceAfterDiscount;
    private BigDecimal totalAmountOfProduct;

}
