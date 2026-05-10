package com.nextgenmanager.nextgenmanager.bom.repository.routing;

import com.nextgenmanager.nextgenmanager.bom.model.routing.RoutingOperationDependency;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RoutingOperationDependencyRepository extends JpaRepository<RoutingOperationDependency, Long> {

    List<RoutingOperationDependency> findByRoutingOperationId(Long routingOperationId);

    List<RoutingOperationDependency> findByDependsOnRoutingOperationId(Long dependsOnRoutingOperationId);
}
