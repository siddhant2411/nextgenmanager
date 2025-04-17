package com.nextgenmanager.nextgenmanager.production.repository;

import com.nextgenmanager.nextgenmanager.production.model.WorkOrderProductionTemplate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface WorkOrderProductionTemplateRepository extends JpaRepository<WorkOrderProductionTemplate,Integer> {
}
