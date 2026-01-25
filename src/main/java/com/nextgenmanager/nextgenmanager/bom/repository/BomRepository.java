package com.nextgenmanager.nextgenmanager.bom.repository;

import com.nextgenmanager.nextgenmanager.bom.dto.BomDTO;
import com.nextgenmanager.nextgenmanager.bom.model.Bom;
import com.nextgenmanager.nextgenmanager.bom.model.BomPosition;
import com.nextgenmanager.nextgenmanager.production.model.WorkOrderProductionTemplate;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface BomRepository extends JpaRepository<Bom,Integer>, JpaSpecificationExecutor<Bom> {



    @Query("SELECT b FROM Bom b WHERE b.deletedDate IS NULL")
    Page<Bom> findAllActiveBom(Pageable pageable);


    @Query("SELECT b FROM Bom b WHERE b.parentInventoryItem.id = :inventoryItemId AND deletedDate = null")
    List<Bom> findBomByParentInventoryItemId(@Param("inventoryItemId") int inventoryItemId);

    @Query("SELECT w FROM WorkOrderProductionTemplate w WHERE w.bom.id = :bomId")
    WorkOrderProductionTemplate findWOTemplateByBomId(@Param("bomId") int bomId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT COALESCE(MAX(b.versionNumber), 0) FROM Bom b WHERE b.parentInventoryItem.inventoryItemId = :itemId")
    int findMaxVersionNumber(@Param("itemId") Integer itemId);


}
