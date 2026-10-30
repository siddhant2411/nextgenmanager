package com.nextgenmanager.nextgenmanager.sales.dto;

import com.nextgenmanager.nextgenmanager.sales.model.SalesReturnGoodsCondition;
import lombok.Data;

@Data
public class SalesCreditNoteLineRequest {
    private int inventoryItemId;
    private int lineNumber;
    private double returnedQty;
    private double rate;
    private double gstRate;       // e.g. 18.0
    private String warehouseTo;
    private String remarks;
    /** Null means decided from the note's reason. */
    private SalesReturnGoodsCondition goodsCondition;
}
