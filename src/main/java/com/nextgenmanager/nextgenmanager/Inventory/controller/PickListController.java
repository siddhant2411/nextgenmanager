package com.nextgenmanager.nextgenmanager.Inventory.controller;

import com.nextgenmanager.nextgenmanager.Inventory.dto.PickConfirmRequest;
import com.nextgenmanager.nextgenmanager.Inventory.dto.PickListCreateRequest;
import com.nextgenmanager.nextgenmanager.Inventory.dto.PickListDto;
import com.nextgenmanager.nextgenmanager.Inventory.model.PickListStatus;
import com.nextgenmanager.nextgenmanager.Inventory.service.PickListService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Pick lists. Confirming a pick is deliberately open to any authenticated user with inventory
 * access rather than admins only — the person doing it is a picker on the floor, not an
 * administrator.
 */
@RestController
@RequestMapping("/api/pick-list")
@RequiredArgsConstructor
public class PickListController {

    private final PickListService pickListService;

    @GetMapping
    public ResponseEntity<List<PickListDto>> list(
            @RequestParam(required = false) PickListStatus status,
            @RequestParam(required = false) Long salesOrderId) {
        return ResponseEntity.ok(pickListService.list(status, salesOrderId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<PickListDto> get(@PathVariable Long id) {
        return ResponseEntity.ok(pickListService.get(id));
    }

    @PostMapping
    public ResponseEntity<PickListDto> create(@RequestBody PickListCreateRequest request) {
        return ResponseEntity.ok(pickListService.createFromSalesOrder(request));
    }

    @PostMapping("/{id}/release")
    public ResponseEntity<PickListDto> release(@PathVariable Long id) {
        return ResponseEntity.ok(pickListService.release(id));
    }

    @PostMapping("/{id}/confirm")
    public ResponseEntity<PickListDto> confirm(
            @PathVariable Long id,
            @RequestBody(required = false) PickConfirmRequest request) {
        return ResponseEntity.ok(pickListService.confirm(id, request));
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<Void> cancel(@PathVariable Long id) {
        pickListService.cancel(id);
        return ResponseEntity.noContent().build();
    }
}
