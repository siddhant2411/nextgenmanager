package com.nextgenmanager.nextgenmanager.production.repository;

import com.nextgenmanager.nextgenmanager.production.model.WorkOrderProductionTemplate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface WorkOrderProductionTemplateRepository extends JpaRepository<WorkOrderProductionTemplate,Integer> {

    @Query(value = "SELECT * FROM work_order_production_template WHERE bom_id = :bomId", nativeQuery = true)
    Optional<WorkOrderProductionTemplate> findByBomId(@Param("bomId") int bomId);

}
