package com.nextgenmanager.nextgenmanager.production.repository;


import com.nextgenmanager.nextgenmanager.production.model.WorkOrderProductionTemplateDocument;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface WorkOrderProductionTemplateDocumentRepository extends JpaRepository<WorkOrderProductionTemplateDocument,Integer> {
}
