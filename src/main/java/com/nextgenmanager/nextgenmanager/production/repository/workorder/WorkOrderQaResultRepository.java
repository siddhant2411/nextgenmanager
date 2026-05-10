package com.nextgenmanager.nextgenmanager.production.repository.workorder;

import com.nextgenmanager.nextgenmanager.production.model.WorkOrderQaResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface WorkOrderQaResultRepository extends JpaRepository<WorkOrderQaResult, Long> {
}
