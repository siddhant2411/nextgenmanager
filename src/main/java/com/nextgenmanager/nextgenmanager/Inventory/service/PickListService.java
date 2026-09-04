package com.nextgenmanager.nextgenmanager.Inventory.service;

import com.nextgenmanager.nextgenmanager.Inventory.dto.PickConfirmRequest;
import com.nextgenmanager.nextgenmanager.Inventory.dto.PickListCreateRequest;
import com.nextgenmanager.nextgenmanager.Inventory.dto.PickListDto;
import com.nextgenmanager.nextgenmanager.Inventory.model.PickListStatus;

import java.util.List;

public interface PickListService {

    List<PickListDto> list(PickListStatus status, Long salesOrderId);

    PickListDto get(Long id);

    /** Builds a pick for whatever on the order is not already covered by another live pick. */
    PickListDto createFromSalesOrder(PickListCreateRequest request);

    /** Sends it to the floor. */
    PickListDto release(Long id);

    /** Records what was found and which specific units were taken. */
    PickListDto confirm(Long id, PickConfirmRequest request);

    /** Abandons the pick and releases every allocation back to pickable stock. */
    void cancel(Long id);
}
