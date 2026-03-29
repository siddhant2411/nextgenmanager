package com.nextgenmanager.nextgenmanager.items.repository;

import com.nextgenmanager.nextgenmanager.items.model.ItemCodeSeries;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ItemCodeSeriesRepository extends JpaRepository<ItemCodeSeries, Long> {

    List<ItemCodeSeries> findByIsActiveTrueAndDeletedDateIsNull();

    /** Find series by ID with PESSIMISTIC_WRITE lock for atomic code generation. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM ItemCodeSeries s WHERE s.id = ?1")
    Optional<ItemCodeSeries> findByIdWithLock(Long id);
}
