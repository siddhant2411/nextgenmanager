package com.nextgenmanager.nextgenmanager.items.controller;

import com.nextgenmanager.nextgenmanager.items.model.ItemCodeSeries;
import com.nextgenmanager.nextgenmanager.items.repository.ItemCodeSeriesRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/item-code-series")
@PreAuthorize("hasAnyAuthority('ROLE_SUPER_ADMIN','ROLE_ADMIN','ROLE_USER'," +
        "'ROLE_INVENTORY_ADMIN','ROLE_INVENTORY_USER','ROLE_SALES_ADMIN','ROLE_SALES_USER'," +
        "'ROLE_PRODUCTION_ADMIN','ROLE_PRODUCTION_USER')")
public class ItemCodeSeriesController {

    @Autowired
    private ItemCodeSeriesRepository repo;

    /** List all active series (used by the series picker in the item form). */
    @GetMapping
    public List<ItemCodeSeries> getAll() {
        return repo.findByIsActiveTrueAndDeletedDateIsNull();
    }

    /** Create a new series. Admin / Inventory Admin only. */
    @PostMapping
    @PreAuthorize("hasAnyAuthority('ROLE_SUPER_ADMIN','ROLE_ADMIN','ROLE_INVENTORY_ADMIN')")
    public ResponseEntity<ItemCodeSeries> create(@RequestBody ItemCodeSeries series) {
        series.setId(null);        // force insert
        series.setLastNumber(0);   // always starts at 0
        return ResponseEntity.status(HttpStatus.CREATED).body(repo.save(series));
    }

    /** Preview the next code for a series WITHOUT consuming (incrementing) it. */
    @GetMapping("/{id}/preview")
    public ResponseEntity<Map<String, String>> preview(@PathVariable Long id) {
        return repo.findById(id)
                .map(s -> ResponseEntity.ok(Map.of("code", s.previewNextCode(), "series", s.getSeriesCode())))
                .orElse(ResponseEntity.notFound().build());
    }

    /** Atomically consume and return the next code for a series. Uses pessimistic lock to prevent duplicates. */
    @PostMapping("/{id}/consume")
    @Transactional
    public ResponseEntity<Map<String, String>> consumeNextCode(@PathVariable Long id) {
        return repo.findByIdWithLock(id)
                .map(s -> {
                    String code = s.consumeNextCode();
                    repo.save(s);
                    return ResponseEntity.ok(Map.of("code", code, "series", s.getSeriesCode()));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    /** Soft-delete a series. */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('ROLE_SUPER_ADMIN','ROLE_ADMIN','ROLE_INVENTORY_ADMIN')")
    public ResponseEntity<?> delete(@PathVariable Long id) {
        return repo.findById(id).map(s -> {
            s.setDeletedDate(new java.util.Date());
            repo.save(s);
            return ResponseEntity.ok().build();
        }).orElse(ResponseEntity.notFound().build());
    }
}
