package com.nextgenmanager.nextgenmanager.Inventory.service;

import com.nextgenmanager.nextgenmanager.Inventory.dto.StockTransferCreateRequest;
import com.nextgenmanager.nextgenmanager.Inventory.dto.StockTransferDto;
import com.nextgenmanager.nextgenmanager.Inventory.dto.StockTransferReceiveRequest;
import com.nextgenmanager.nextgenmanager.Inventory.model.StockTransferStatus;

import java.util.List;

public interface StockTransferService {

    List<StockTransferDto> list(StockTransferStatus status, Long warehouseId);

    StockTransferDto get(Long id);

    /** Creates the transfer in DRAFT. Nothing moves yet. */
    StockTransferDto create(StockTransferCreateRequest request);

    /** Takes the stock out of the source warehouse and puts it in transit. */
    StockTransferDto dispatch(Long id);

    /** Lands what arrived in the destination. A shortfall stays in transit at the source. */
    StockTransferDto receive(Long id, StockTransferReceiveRequest request);

    /** Only from DRAFT — once dispatched, stock has moved and must be received or investigated. */
    void cancel(Long id);
}
