package com.nextgenmanager.nextgenmanager.sales.exception;

import java.util.List;

public class InsufficientStockForDeliveryException extends RuntimeException {

    private final List<ShortfallDetail> shortfalls;

    public InsufficientStockForDeliveryException(List<ShortfallDetail> shortfalls) {
        super("Insufficient stock for " + shortfalls.size() + " item(s). Store request(s) have been raised.");
        this.shortfalls = shortfalls;
    }

    public List<ShortfallDetail> getShortfalls() {
        return shortfalls;
    }

    public record ShortfallDetail(
            String itemCode,
            String itemName,
            double requestedQty,
            double availableQty,
            Long storeRequestId,
            String storeRequestRef
    ) {}
}
