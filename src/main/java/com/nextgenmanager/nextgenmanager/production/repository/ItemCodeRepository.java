package com.nextgenmanager.nextgenmanager.production.repository;

import com.nextgenmanager.nextgenmanager.items.model.ItemCode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ItemCodeRepository extends JpaRepository<ItemCode,Long> {

    @Query("SELECT MAX(p.sequenceNumber) FROM ItemCode p WHERE p.year = :year")
    Integer findMaxSequenceForYear(@Param("year") int year);
}
