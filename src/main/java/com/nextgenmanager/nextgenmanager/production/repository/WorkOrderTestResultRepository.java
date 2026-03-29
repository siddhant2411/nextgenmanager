package com.nextgenmanager.nextgenmanager.production.repository;

import com.nextgenmanager.nextgenmanager.production.model.WorkOrderTestResult;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface WorkOrderTestResultRepository extends JpaRepository<WorkOrderTestResult, Long> {

    List<WorkOrderTestResult> findByWorkOrderIdOrderBySequence(int workOrderId);
}
