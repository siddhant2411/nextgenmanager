package com.nextgenmanager.nextgenmanager.bom.repository;

import com.nextgenmanager.nextgenmanager.bom.model.Bom;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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


    @Query("SELECT b FROM Bom b " +
           "JOIN FETCH b.parentInventoryItem pi " +
           "LEFT JOIN FETCH pi.productSpecification " +
           "LEFT JOIN FETCH pi.productInventorySettings " +
           "LEFT JOIN FETCH pi.productFinanceSettings " +
           "WHERE pi.inventoryItemId = :inventoryItemId AND b.deletedDate IS NULL")
    List<Bom> findBomByParentInventoryItemId(@Param("inventoryItemId") int inventoryItemId);



    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT COALESCE(MAX(b.versionNumber), 0) FROM Bom b WHERE b.parentInventoryItem.inventoryItemId = :itemId")
    int findMaxVersionNumber(@Param("itemId") Integer itemId);

    @Query("SELECT b FROM Bom b WHERE b.parentInventoryItem.inventoryItemId IN :itemIds " +
           "AND b.bomStatus = 3 AND b.isActiveVersion = true AND b.deletedDate IS NULL")
    List<Bom> findActiveBomsByParentItemIds(@Param("itemIds") List<Integer> itemIds);

    @Query("SELECT b FROM Bom b " +
           "JOIN FETCH b.positions p " +
           "JOIN FETCH p.childInventoryItem ci " +
           "LEFT JOIN FETCH ci.productSpecification " +
           "LEFT JOIN FETCH ci.productFinanceSettings " +
           "WHERE b.parentInventoryItem.inventoryItemId = :itemId " +
           "AND b.bomStatus = 3 AND b.isActiveVersion = true AND b.deletedDate IS NULL")
    java.util.Optional<Bom> findActiveBomWithPositionsByParentItemId(@Param("itemId") int itemId);

}
