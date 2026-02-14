package com.nextgenmanager.nextgenmanager.production.repository.audit;

import com.nextgenmanager.nextgenmanager.production.helper.WorkOrderHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface WorkOrderHistoryRepository extends JpaRepository<WorkOrderHistory,Long> {

    public List<WorkOrderHistory> findByWorkOrderId(int workOrderId);

}
