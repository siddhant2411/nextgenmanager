package com.nextgenmanager.nextgenmanager.sales.repository;

import com.nextgenmanager.nextgenmanager.sales.model.DeliveryNote;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

@Repository
public interface DeliveryNoteRepository extends JpaRepository<DeliveryNote, Long>, JpaSpecificationExecutor<DeliveryNote> {
    Page<DeliveryNote> findBySalesOrderId(Long salesOrderId, Pageable pageable);
}
