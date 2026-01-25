package com.nextgenmanager.nextgenmanager.production.repository;

import com.nextgenmanager.nextgenmanager.production.model.Routing;
import com.nextgenmanager.nextgenmanager.production.model.RoutingOperation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface  RoutingRepository extends JpaRepository<Routing, Long> {
    Optional<Routing> findByBomId(Integer bomId);
}

