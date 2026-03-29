package com.nextgenmanager.nextgenmanager.production.repository;

import com.nextgenmanager.nextgenmanager.production.model.RoutingOperationDependency;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RoutingOperationDependencyRepository extends JpaRepository<RoutingOperationDependency, Long> {

    List<RoutingOperationDependency> findByRoutingOperationId(Long routingOperationId);

    List<RoutingOperationDependency> findByDependsOnRoutingOperationId(Long dependsOnRoutingOperationId);
}
