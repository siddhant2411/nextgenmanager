package com.nextgenmanager.nextgenmanager.bom.repository;

import com.nextgenmanager.nextgenmanager.bom.dto.BomDTO;
import com.nextgenmanager.nextgenmanager.bom.model.Bom;
import com.nextgenmanager.nextgenmanager.bom.model.BomPosition;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface BomRepository extends JpaRepository<Bom,Integer> {

    @Query(
            value = "SELECT new com.nextgenmanager.nextgenmanager.bom.dto.BomDTO(b.id, b.bomName, i.itemCode, i.name) " +
                    "FROM Bom b " +
                    "JOIN b.parentInventoryItem i " +  // Assuming the relationship is mapped correctly in Bom entity
                    "WHERE b.deletedDate IS NULL " +
                    "AND (LOWER(b.bomName) LIKE %:search% " +
                    "OR LOWER(i.itemCode) LIKE %:search% " +
                    "OR LOWER(i.name) LIKE %:search%)"
    )
    Page<BomDTO> findAllActiveBom(@Param("search") String search, Pageable pageable);

}
