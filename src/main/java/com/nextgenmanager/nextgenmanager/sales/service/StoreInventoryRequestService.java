package com.nextgenmanager.nextgenmanager.sales.service;

import com.nextgenmanager.nextgenmanager.Inventory.model.InventoryApprovalStatus;
import com.nextgenmanager.nextgenmanager.Inventory.model.InventoryRequest;
import com.nextgenmanager.nextgenmanager.Inventory.model.InventoryRequestSource;
import com.nextgenmanager.nextgenmanager.Inventory.repository.InventoryRequestRepository;
import com.nextgenmanager.nextgenmanager.Inventory.service.InventoryInstanceService;
import com.nextgenmanager.nextgenmanager.sales.repository.SalesOrderItemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Bridges sales-side DN creation with inventory store requests.
 * Runs in REQUIRES_NEW so the store request is committed even when
 * the caller's transaction rolls back (e.g. due to insufficient stock throw).
 */
@Service
@RequiredArgsConstructor
public class StoreInventoryRequestService {

    private final InventoryInstanceService inventoryInstanceService;
    private final InventoryRequestRepository inventoryRequestRepository;
    private final SalesOrderItemRepository salesOrderItemRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public InventoryRequest createOrFetchStoreRequest(
            int itemId,
            double qty,
            Long soId,
            Long soItemId,
            String requestedBy) {

        // Avoid duplicate pending requests for the same SO + item
        List<InventoryRequest> existing =
                inventoryRequestRepository.findBySourceIdAndRequestSource(soId, InventoryRequestSource.SALES_ORDER);
        Optional<InventoryRequest> alreadyPending = existing.stream()
                .filter(r -> r.getInventoryItem() != null
                        && r.getInventoryItem().getInventoryItemId() == itemId
                        && r.getApprovalStatus() == InventoryApprovalStatus.PENDING)
                .findFirst();
        if (alreadyPending.isPresent()) {
            return alreadyPending.get();
        }

        InventoryRequest request = inventoryInstanceService.requestInstanceByItemId(
                itemId, qty, InventoryRequestSource.SALES_ORDER, soId,
                requestedBy, "Auto-raised: insufficient stock for delivery challan");

        // Link back so the next DN attempt routes through the request-consumption path
        if (soItemId != null) {
            salesOrderItemRepository.findById(soItemId).ifPresent(soItem -> {
                soItem.setItemRequestId(request.getId());
                salesOrderItemRepository.save(soItem);
            });
        }

        return request;
    }
}
