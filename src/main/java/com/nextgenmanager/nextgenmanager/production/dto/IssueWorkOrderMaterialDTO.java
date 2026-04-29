package com.nextgenmanager.nextgenmanager.production.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;


@Data
@NoArgsConstructor
@AllArgsConstructor
public class IssueWorkOrderMaterialDTO {

    private int workOrderId;

    /**
     * List of materials to issue with their quantities
     */
    private List<MaterialIssueItem> materials;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MaterialIssueItem {

        /**
         * WorkOrderMaterial ID
         */
        private Long workOrderMaterialId;

        /**
         * Quantity to issue
         */
        private BigDecimal issuedQuantity;

        /**
         * Scrapped quantity (optional)
         */
        private BigDecimal scrappedQuantity = BigDecimal.ZERO;
        private List<Long> overrideInstanceIds;
        private String overrideReason;
    }
}
