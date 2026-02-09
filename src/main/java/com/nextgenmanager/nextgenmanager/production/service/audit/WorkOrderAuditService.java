package com.nextgenmanager.nextgenmanager.production.service.audit;

import com.nextgenmanager.nextgenmanager.production.enums.WorkOrderEventType;
import com.nextgenmanager.nextgenmanager.production.helper.WorkOrderHistory;
import com.nextgenmanager.nextgenmanager.production.model.WorkOrder;
import com.nextgenmanager.nextgenmanager.production.repository.audit.WorkOrderHistoryRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Date;

@Service
public class WorkOrderAuditService {

    @Autowired
    private WorkOrderHistoryRepository historyRepository;

    public void record(
            WorkOrder workOrder,
            WorkOrderEventType eventType,
            String field,
            String oldVal,
            String newVal,
            String remarks
    ) {
        WorkOrderHistory h = new WorkOrderHistory();
        h.setWorkOrder(workOrder);
        h.setEventType(eventType);
        h.setFieldName(field);
        h.setOldValue(oldVal);
        h.setNewValue(newVal);
        h.setPerformedBy(getCurrentUser());
        h.setPerformedAt(new Date());
        h.setRemarks(remarks);

        historyRepository.save(h);
    }

    private String getCurrentUser() {
        // Later integrate Spring Security
        return "SYSTEM";
    }
}
