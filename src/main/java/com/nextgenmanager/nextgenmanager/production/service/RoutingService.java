package com.nextgenmanager.nextgenmanager.production.service;

import com.nextgenmanager.nextgenmanager.production.dto.RoutingOperationDto;
import com.nextgenmanager.nextgenmanager.production.dto.RoutingDto;
import com.nextgenmanager.nextgenmanager.production.model.Routing;
import com.nextgenmanager.nextgenmanager.production.model.RoutingOperation;
import jakarta.xml.bind.ValidationException;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public interface RoutingService {


    // ----------------------------------------------------------
    // CREATE or UPDATE ROUTING
    // ----------------------------------------------------------
    @Transactional
    RoutingDto createOrUpdateRouting(Integer bomId, RoutingDto dto, String actor);

    Routing updateOperations(Long routingId, List<RoutingOperationDto> operations, String actor);

    void approve(Long routingId, String actor) throws ValidationException;

    void activate(Long routingId, String actor) throws ValidationException;

    void obsolete(Long routingId, String actor);

    RoutingDto getByBom(Integer bomId);

    Routing getRoutingEntityByBom(Integer bomId);

    public List<RoutingOperation> getOperationsEntities(Long routingId);

    RoutingDto getRouting(Long id);

    List<RoutingOperationDto> getOperations(Long routingId);
}
