package com.nextgenmanager.nextgenmanager.company.controller;

import com.nextgenmanager.nextgenmanager.company.dto.CompanyDetailsDTO;
import com.nextgenmanager.nextgenmanager.company.dto.CompanyDetailsRequestDTO;
import com.nextgenmanager.nextgenmanager.company.service.CompanyDetailsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import com.nextgenmanager.nextgenmanager.common.security.authorization.RequiresAdminOnly;
import com.nextgenmanager.nextgenmanager.common.security.authorization.RequiresAuthenticated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/company")
@Tag(name = "Company Details", description = "Own company information used in documents and reports")
public class CompanyDetailsController {

    @Autowired
    private CompanyDetailsService companyDetailsService;

    @GetMapping
    @Operation(summary = "Get own company details")
    @RequiresAuthenticated
    public ResponseEntity<CompanyDetailsDTO> get() {
        return ResponseEntity.ok(companyDetailsService.get());
    }

    @PutMapping
    @Operation(summary = "Create or update own company details (admin only)")
    @RequiresAdminOnly
    public ResponseEntity<CompanyDetailsDTO> upsert(@Valid @RequestBody CompanyDetailsRequestDTO request) {
        return ResponseEntity.ok(companyDetailsService.upsert(request));
    }
}
