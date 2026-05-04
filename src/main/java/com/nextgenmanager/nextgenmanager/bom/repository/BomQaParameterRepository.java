package com.nextgenmanager.nextgenmanager.bom.repository;

import com.nextgenmanager.nextgenmanager.bom.model.BomQaParameter;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface BomQaParameterRepository extends JpaRepository<BomQaParameter, Long> {

    List<BomQaParameter> findByRoutingOperationIdAndDeletedDateIsNull(Long routingOperationId);
}
