package com.nextgenmanager.nextgenmanager.bom.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.nextgenmanager.nextgenmanager.bom.model.BomPosition;
import com.nextgenmanager.nextgenmanager.items.model.InventoryItem;
import com.nextgenmanager.nextgenmanager.production.model.RoutingOperation;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class BomPositionRequestDTO {

    private InventoryItem childInventoryItem;
    private Integer position;
    private Double quantity;
    private BigDecimal scrapPercentage;
    private Long routingOperationId;
    private Integer routingOperationSequenceNumber;

    public BomPosition toEntity() {
        BomPosition entity = new BomPosition();

        entity.setChildInventoryItem(childInventoryItem);
        if (position != null) {
            entity.setPosition(position);
        }
        entity.setQuantity(quantity != null ? quantity : 0.0d);
        entity.setScrapPercentage(scrapPercentage);

        if (routingOperationId != null) {
            RoutingOperation routingOperation = new RoutingOperation();
            routingOperation.setId(routingOperationId);
            entity.setRoutingOperation(routingOperation);
        }

        return entity;
    }
}
