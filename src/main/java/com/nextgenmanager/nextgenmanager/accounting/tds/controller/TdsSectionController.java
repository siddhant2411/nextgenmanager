package com.nextgenmanager.nextgenmanager.accounting.tds.controller;

import com.nextgenmanager.nextgenmanager.accounting.tds.dto.TdsSectionCreateDto;
import com.nextgenmanager.nextgenmanager.accounting.tds.dto.TdsSectionDto;
import com.nextgenmanager.nextgenmanager.accounting.tds.service.TdsSectionService;
import com.nextgenmanager.nextgenmanager.common.security.authorization.RequiresAccountingAccess;
import com.nextgenmanager.nextgenmanager.common.security.authorization.RequiresAccountsHead;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/accounting/tds/sections")
@RequiresAccountingAccess
@RequiredArgsConstructor
public class TdsSectionController {

    private final TdsSectionService sectionService;

    @GetMapping
    public ResponseEntity<List<TdsSectionDto>> list(
            @RequestParam(name = "activeOnly", defaultValue = "false") boolean activeOnly) {
        return ResponseEntity.ok(activeOnly ? sectionService.listActive() : sectionService.listAll());
    }

    @PostMapping
    @RequiresAccountsHead
    public ResponseEntity<TdsSectionDto> create(@Valid @RequestBody TdsSectionCreateDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(sectionService.create(dto));
    }

    @PutMapping("/{id}")
    @RequiresAccountsHead
    public ResponseEntity<TdsSectionDto> update(@PathVariable Long id, @Valid @RequestBody TdsSectionCreateDto dto) {
        return ResponseEntity.ok(sectionService.update(id, dto));
    }

    @DeleteMapping("/{id}")
    @RequiresAccountsHead
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        sectionService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
