package com.nextgenmanager.nextgenmanager.bom.repository;

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
public interface BomPositionRepository extends JpaRepository<BomPosition,Integer> {
    @Query(value = "SELECT * FROM bomPosition b where b.bomId= :id",nativeQuery = true)
    public List<BomPosition> getBomPositionList(int id);



}
