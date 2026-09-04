package com.nextgenmanager.nextgenmanager.Inventory.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * Receives a dispatched transfer. Omitting a line receives it in full; naming one with a smaller
 * quantity records a short receipt, and the shortfall stays inTransit at the source.
 */
public record StockTransferReceiveRequest(
        List<Line> lines,
        String remarks
) {
    public record Line(Long lineId, BigDecimal receivedQuantity) {}
}
