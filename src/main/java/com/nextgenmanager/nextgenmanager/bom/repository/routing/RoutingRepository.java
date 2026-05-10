package com.nextgenmanager.nextgenmanager.bom.repository.routing;

import com.nextgenmanager.nextgenmanager.bom.model.routing.Routing;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface  RoutingRepository extends JpaRepository<Routing, Long> {
    Optional<Routing> findByBomId(Integer bomId);
}

