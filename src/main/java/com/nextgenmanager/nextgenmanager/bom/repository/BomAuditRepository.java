package com.nextgenmanager.nextgenmanager.bom.repository;

import com.nextgenmanager.nextgenmanager.bom.model.BomAudit;
import com.nextgenmanager.nextgenmanager.bom.model.BomHistory;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BomAuditRepository extends JpaRepository<BomAudit,Integer> {

}
