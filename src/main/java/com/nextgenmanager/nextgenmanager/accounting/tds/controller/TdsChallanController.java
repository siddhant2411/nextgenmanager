package com.nextgenmanager.nextgenmanager.accounting.tds.controller;

import com.nextgenmanager.nextgenmanager.accounting.tds.dto.TdsChallanCreateDto;
import com.nextgenmanager.nextgenmanager.accounting.tds.dto.TdsChallanDto;
import com.nextgenmanager.nextgenmanager.accounting.tds.service.TdsChallanService;
import com.nextgenmanager.nextgenmanager.common.security.authorization.RequiresAccountingAccess;
import com.nextgenmanager.nextgenmanager.common.security.authorization.RequiresAccountsHead;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;

@RestController
@RequestMapping("/api/accounting/tds/challans")
@RequiresAccountingAccess
@RequiredArgsConstructor
public class TdsChallanController {

    private final TdsChallanService challanService;

    @GetMapping
    public ResponseEntity<List<TdsChallanDto>> list(@RequestParam(required = false) String fy) {
        return ResponseEntity.ok(challanService.listChallans(fy));
    }

    @GetMapping("/{id}")
    public ResponseEntity<TdsChallanDto> get(@PathVariable Long id) {
        return ResponseEntity.ok(challanService.getChallan(id));
    }

    @PostMapping
    @RequiresAccountsHead
    public ResponseEntity<TdsChallanDto> create(@Valid @RequestBody TdsChallanCreateDto dto, Principal principal) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(challanService.createChallan(dto, principal.getName()));
    }
}
