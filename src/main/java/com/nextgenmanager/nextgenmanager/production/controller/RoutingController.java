package com.nextgenmanager.nextgenmanager.production.controller;

import com.nextgenmanager.nextgenmanager.production.dto.RoutingOperationDto;
import com.nextgenmanager.nextgenmanager.production.dto.RoutingDto;
import com.nextgenmanager.nextgenmanager.production.model.Routing;
import com.nextgenmanager.nextgenmanager.production.model.RoutingOperation;
import com.nextgenmanager.nextgenmanager.production.service.RoutingService;
import jakarta.xml.bind.ValidationException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/manufacturing/routing")
public class RoutingController {

    private final RoutingService routingService;

    @Autowired
    public RoutingController(RoutingService routingService) {
        this.routingService = routingService;
    }

    // ---------------------------------------------------------------------------
    // CREATE or UPDATE Routing (for a BOM)
    // ---------------------------------------------------------------------------
    @PostMapping("/bom/{bomId}")
    public ResponseEntity<RoutingDto> createOrUpdateRouting(
            @PathVariable Integer bomId,
            @RequestBody RoutingDto routingDto,
            @RequestHeader("X-Actor") String actor) {

        routingDto.setBomId(bomId);

        RoutingDto routing = routingService.createOrUpdateRouting(bomId, routingDto, actor);

        return ResponseEntity.ok(routing);
    }

    // ---------------------------------------------------------------------------
    // UPDATE Operations Only
    // ---------------------------------------------------------------------------
    @PutMapping("/{routingId}/operations")
    public ResponseEntity<Routing> updateOperations(
            @PathVariable Long routingId,
            @RequestBody List<RoutingOperationDto> operations,
            @RequestHeader("X-Actor") String actor) {

        Routing updated = routingService.updateOperations(routingId, operations, actor);

        return ResponseEntity.ok(updated);
    }

    // ---------------------------------------------------------------------------
    // APPROVE Routing
    // ---------------------------------------------------------------------------
    @PostMapping("/{routingId}/approve")
    public ResponseEntity<Void> approve(
            @PathVariable Long routingId,
            @RequestHeader("X-Actor") String actor) throws ValidationException {

        routingService.approve(routingId, actor);
        return ResponseEntity.noContent().build();
    }

    // ---------------------------------------------------------------------------
    // ACTIVATE Routing
    // ---------------------------------------------------------------------------
    @PostMapping("/{routingId}/activate")
    public ResponseEntity<Void> activate(
            @PathVariable Long routingId,
            @RequestHeader("X-Actor") String actor) throws ValidationException {

        routingService.activate(routingId, actor);
        return ResponseEntity.noContent().build();
    }

    // ---------------------------------------------------------------------------
    // OBSOLETE Routing
    // ---------------------------------------------------------------------------
    @PostMapping("/{routingId}/obsolete")
    public ResponseEntity<Void> obsolete(
            @PathVariable Long routingId,
            @RequestHeader("X-Actor") String actor) {

        routingService.obsolete(routingId, actor);
        return ResponseEntity.noContent().build();
    }

    // ---------------------------------------------------------------------------
    // GET Routing for BOM
    // ---------------------------------------------------------------------------
    @GetMapping("/bom/{bomId}")
    public ResponseEntity<RoutingDto> getByBom(@PathVariable Integer bomId) {
        RoutingDto routing = routingService.getByBom(bomId);
        return ResponseEntity.ok(routing);
    }

    @GetMapping("/{id}")
    public ResponseEntity<RoutingDto> getRouting(@PathVariable Long id) {
        RoutingDto routing = routingService.getRouting(id);
        return ResponseEntity.ok(routing);
    }

    // ---------------------------------------------------------------------------
    // GET all operations for a routing
    // ---------------------------------------------------------------------------
    @GetMapping("/{routingId}/operations")
    public ResponseEntity<List<RoutingOperationDto>> getOperations(
            @PathVariable Long routingId) {

        return ResponseEntity.ok(routingService.getOperations(routingId));
    }
}
