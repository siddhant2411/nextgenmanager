package com.nextgenmanager.nextgenmanager.items.repository;

import com.nextgenmanager.nextgenmanager.items.model.ProcessType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProcessTypeRepository extends JpaRepository<ProcessType, Long> {
    List<ProcessType> findByIsActiveTrueOrderByNameAsc();
}
