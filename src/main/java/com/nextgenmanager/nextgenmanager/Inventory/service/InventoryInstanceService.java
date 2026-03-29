package com.nextgenmanager.nextgenmanager.Inventory.service;


import com.nextgenmanager.nextgenmanager.Inventory.dto.*;
import com.nextgenmanager.nextgenmanager.Inventory.model.*;
import com.nextgenmanager.nextgenmanager.items.model.InventoryItem;
import com.nextgenmanager.nextgenmanager.items.model.ItemType;
import com.nextgenmanager.nextgenmanager.items.model.UOM;
import io.swagger.models.auth.In;
import org.springframework.data.domain.Page;

import java.util.List;
import java.util.Map;

public interface InventoryInstanceService {

    public List<InventoryInstance> getAllInventoryInstances(int page, int size, String sortBy, String sortDir, String query);

    public Page<InventoryPresentDTO> getPresentInventoryInstances(int page, int size, String sortBy, String sortDir, String queryItemCode,
                                                                  String queryItemName, String queryHsnCode, Double totalQuantityCondition, String filterType, UOM queryUOM, ItemType itemType);

    public List<InventoryInstance> getInventoryInstanceByItemId(int inventoryItemId, int page, int size, String sortBy, String sortDir, String query);

    public List<InventoryInstance> createInstances(InventoryItem item, double qty, InventoryInstance template);


    public List<InventoryInstance> consumeInventoryInstance(InventoryItem item, double qty, Long requestId);

    public List<InventoryInstance> bookInventoryInstance(InventoryItem inventoryItem, double bookedQty);

//    public List<InventoryInstance> requestInstance(InventoryItem inventoryItem, double requestedQty);

    public InventoryInstance updateInventoryInstance(InventoryInstance inventoryInstance);

    public void deleteInventoryInstance(long id);

    public InventoryInstance getInventoryInstanceById(long id);

    public void updateItemAvailability(int itemId);

    public void revertInventoryInstances(List<InventoryInstance> instances);

    public Page<GroupedInventoryItem> getGroupedInventoryInstances(int page, int size, String sortBy, String sortDir,
                                                                   String queryItemCode, String queryItemName, String queryHsnCode,
                                                                   Double totalQuantityCondition, String filterType, UOM queryUOM,
                                                                   ItemType itemType,
                                                                   InventoryApprovalStatus approvalStatusFilter,
                                                                   ProcurementDecision procurementDecisionFilter);


//    public List<InventoryInstance> markRequestedInventoryAsArrived(List<Long> instanceIds);

    public Map<String, Object> getInventorySummary();

    public InventoryRequest requestInstance(InventoryItem item, double qty, InventoryRequestSource source, Long sourceId, String requestedBy, String requestRemarks);

//    public List<InventoryInstance> approveInventoryRequest(List<Long> instanceIds, ProcurementDecision decision);

    public InventoryRequest requestInstanceByItemId(int itemId, double qty, InventoryRequestSource source, Long sourceId,String requestedBy,String requestRemarks);

    public List<InventoryInstance> addInventory(AddInventoryRequest request);

    public List<InventoryInstance> approveInventoryRequest(Long requestId, String approvedBy,String approveRemarks);

    public Page<InventoryRequestGroupDTO> getGroupedRequests(
            int page, int size,
            String itemCode, String itemName,
            InventoryRequestSource source,
            InventoryApprovalStatus approvalStatus,
            Long referenceId
    );

    public Page<InventoryProcurementOrderDTO> getProcurementOrders(
            int page, int size,
            InventoryProcurementStatus status,
            Long inventoryItemId,
            String createdBy
    );

    public void updateProcurementStatus(Long procurementOrderId, InventoryProcurementStatus newStatus);

    public List<InventoryInstance> getRequestedInstancesByReferenceAndItem(Long referenceId);

    public List<InventoryInstance> rejectInventoryRequest(Long requestId, String rejectedBy, String rejectRemarks);

    public List<InventoryInstance> addInventoryToExistingProcurement(AddInventoryRequest request, long procurementOrderId);

    public InventoryProcurementOrderDTO completeProcurementOrder(Long orderId, String completedBy);
}
