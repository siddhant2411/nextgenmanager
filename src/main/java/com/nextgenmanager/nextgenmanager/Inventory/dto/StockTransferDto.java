package com.nextgenmanager.nextgenmanager.Inventory.dto;

import com.nextgenmanager.nextgenmanager.Inventory.model.StockTransferStatus;

import java.util.Date;
import java.util.List;

public record StockTransferDto(
        Long id,
        String transferNumber,
        Long fromWarehouseId,
        String fromWarehouseCode,
        Long toWarehouseId,
        String toWarehouseCode,
        StockTransferStatus status,
        Date dispatchedDate,
        Date receivedDate,
        String remarks,
        String createdBy,
        List<StockTransferLineDto> lines
) {}
