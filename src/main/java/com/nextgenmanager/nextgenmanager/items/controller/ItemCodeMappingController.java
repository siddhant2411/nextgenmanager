package com.nextgenmanager.nextgenmanager.items.controller;


import com.nextgenmanager.nextgenmanager.items.model.ItemCodeMapping;
import com.nextgenmanager.nextgenmanager.items.repository.ItemCodeMappingRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import com.nextgenmanager.nextgenmanager.common.security.authorization.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/item-code-mapping")
@RequiresAllModulesAccess
public class ItemCodeMappingController {

    @Autowired
    private ItemCodeMappingRepository repository;

    // GET all mappings
    @GetMapping
    public ResponseEntity<List<ItemCodeMapping>> getAllMappings() {
        return ResponseEntity.ok(repository.findAll());
    }

    // GET mappings by type
    @GetMapping("/category/{category}")
    public ResponseEntity<List<ItemCodeMapping>> getMappingsByType(@PathVariable String category) {
        return ResponseEntity.ok(repository.findByCategoryIgnoreCase(category));
    }

    // POST create new mapping
    @PostMapping
    @org.springframework.security.access.prepost.PreAuthorize("hasAnyAuthority('ROLE_SUPER_ADMIN','ROLE_ADMIN','ROLE_PRODUCTION_ADMIN','ROLE_INVENTORY_ADMIN','ROLE_PURCHASE_ADMIN','ROLE_SALES_ADMIN')")
    public ResponseEntity<ItemCodeMapping> createMapping(@RequestBody ItemCodeMapping mapping) {
        return ResponseEntity.ok(repository.save(mapping));
    }

    // PUT update mapping
    @PutMapping("/{id}")
    @org.springframework.security.access.prepost.PreAuthorize("hasAnyAuthority('ROLE_SUPER_ADMIN','ROLE_ADMIN','ROLE_PRODUCTION_ADMIN','ROLE_INVENTORY_ADMIN','ROLE_PURCHASE_ADMIN','ROLE_SALES_ADMIN')")
    public ResponseEntity<ItemCodeMapping> updateMapping(@PathVariable int id, @RequestBody ItemCodeMapping updated) {
        return repository.findById((long) id)
                .map(existing -> {
                    existing.setCategory(updated.getCategory());
                    existing.setKeyword(updated.getKeyword());
                    existing.setCode(updated.getCode());
                    return ResponseEntity.ok(repository.save(existing));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    // DELETE mapping
    @DeleteMapping("/{id}")
    @org.springframework.security.access.prepost.PreAuthorize("hasAnyAuthority('ROLE_SUPER_ADMIN','ROLE_ADMIN','ROLE_PRODUCTION_ADMIN','ROLE_INVENTORY_ADMIN','ROLE_PURCHASE_ADMIN','ROLE_SALES_ADMIN')")
    public ResponseEntity<Object> deleteMapping(@PathVariable int id) {
        return repository.findById((long) id).map(mapping -> {
            repository.delete(mapping);
            return ResponseEntity.noContent().build();
        }).orElse(ResponseEntity.notFound().build());
    }
}

