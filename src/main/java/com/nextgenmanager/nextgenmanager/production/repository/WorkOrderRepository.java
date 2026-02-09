package com.nextgenmanager.nextgenmanager.production.repository;

import com.nextgenmanager.nextgenmanager.production.model.WorkOrder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

public interface WorkOrderRepository extends JpaRepository<WorkOrder,Integer>, JpaSpecificationExecutor<WorkOrder> {
    @Query(value = "SELECT nextval('workOrderSeq')", nativeQuery = true)
    Long getNextWorkOrderSequence();

}
