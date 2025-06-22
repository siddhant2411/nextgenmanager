package com.nextgenmanager.nextgenmanager.items.controller;


import com.nextgenmanager.nextgenmanager.items.model.ItemCodeMapping;
import com.nextgenmanager.nextgenmanager.items.repository.ItemCodeMappingRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/item-code-mapping")
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
    public ResponseEntity<ItemCodeMapping> createMapping(@RequestBody ItemCodeMapping mapping) {
        return ResponseEntity.ok(repository.save(mapping));
    }

    // PUT update mapping
    @PutMapping("/{id}")
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
    public ResponseEntity<Object> deleteMapping(@PathVariable int id) {
        return repository.findById((long) id).map(mapping -> {
            repository.delete(mapping);
            return ResponseEntity.noContent().build();
        }).orElse(ResponseEntity.notFound().build());
    }
}