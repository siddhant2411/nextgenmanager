package com.nextgenmanager.nextgenmanager.bom.repository;

import com.nextgenmanager.nextgenmanager.bom.model.BomAudit;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BomAuditRepository extends JpaRepository<BomAudit,Integer> {

    List<BomAudit> findByBomIdOrderByChangedAtDesc(Integer bomId);
}
