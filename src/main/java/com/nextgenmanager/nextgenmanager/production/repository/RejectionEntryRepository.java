package com.nextgenmanager.nextgenmanager.production.repository;

import com.nextgenmanager.nextgenmanager.production.enums.DispositionStatus;
import com.nextgenmanager.nextgenmanager.production.model.RejectionEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RejectionEntryRepository extends JpaRepository<RejectionEntry, Long> {

    List<RejectionEntry> findByWorkOrderId(int workOrderId);

    List<RejectionEntry> findByWorkOrderIdAndDispositionStatus(int workOrderId, DispositionStatus dispositionStatus);
}
