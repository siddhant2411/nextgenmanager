package com.nextgenmanager.nextgenmanager.purchase.controller;

import com.nextgenmanager.nextgenmanager.purchase.model.PurchaseOrder;
import com.nextgenmanager.nextgenmanager.purchase.repository.PurchaseOrderRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/purchase-orders")
@PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN','INVENTORY_ADMIN','INVENTORY_USER','USER')")
public class PurchaseOrderController {

    @Autowired private PurchaseOrderRepository purchaseOrderRepository;

    @GetMapping
    public ResponseEntity<List<PurchaseOrder>> getAll() {
        return ResponseEntity.ok(purchaseOrderRepository.findByDeletedDateIsNull());
    }

    @GetMapping("/{id}")
    public ResponseEntity<PurchaseOrder> getById(@PathVariable Long id) {
        return purchaseOrderRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}
