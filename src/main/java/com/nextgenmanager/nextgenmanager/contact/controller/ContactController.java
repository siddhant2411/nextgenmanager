package com.nextgenmanager.nextgenmanager.contact.controller;

import com.nextgenmanager.nextgenmanager.contact.dto.ContactDTO;
import com.nextgenmanager.nextgenmanager.contact.dto.ContactRequestDTO;
import com.nextgenmanager.nextgenmanager.contact.dto.ContactSummaryDTO;
import com.nextgenmanager.nextgenmanager.contact.model.ContactType;
import com.nextgenmanager.nextgenmanager.contact.service.ContactService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/contact")
@Tag(name = "Contacts", description = "Vendors, Customers and combined contacts with GST and MSME details")
@PreAuthorize("hasAnyAuthority('ROLE_SUPER_ADMIN','ROLE_ADMIN','ROLE_USER','ROLE_SALES_ADMIN','ROLE_SALES_USER')")
public class ContactController {

    @Autowired
    private ContactService contactService;

    @PostMapping
    @Operation(summary = "Create a new contact (vendor / customer / both)")
    public ResponseEntity<ContactDTO> create(@Valid @RequestBody ContactRequestDTO request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(contactService.create(request));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get full contact details by ID")
    public ResponseEntity<ContactDTO> getById(@PathVariable int id) {
        return ResponseEntity.ok(contactService.getById(id));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update contact")
    public ResponseEntity<ContactDTO> update(@PathVariable int id, @Valid @RequestBody ContactRequestDTO request) {
        return ResponseEntity.ok(contactService.update(id, request));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Soft-delete a contact")
    public ResponseEntity<Void> delete(@PathVariable int id) {
        contactService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping
    @Operation(summary = "Search contacts — paginated, filterable by type")
    public ResponseEntity<Page<ContactDTO>> search(
            @RequestParam(required = false) String query,
            @RequestParam(required = false) ContactType type,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "companyName") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDir) {
        return ResponseEntity.ok(contactService.search(query, type, page, size, sortBy, sortDir));
    }

    /**
     * GET /api/contact/dropdown?query=xyz&type=VENDOR
     * Lightweight search for dropdowns — max 20 results.
     * type=VENDOR → returns VENDOR + BOTH contacts.
     * type=CUSTOMER → returns CUSTOMER + BOTH contacts.
     */
    @GetMapping("/dropdown")
    @Operation(
        summary = "Quick search for dropdowns",
        description = "Returns ContactSummaryDTO list (max 20). " +
                      "Use type=VENDOR for supplier/job-worker fields, type=CUSTOMER for sales fields."
    )
    public ResponseEntity<List<ContactSummaryDTO>> dropdown(
            @RequestParam(required = false, defaultValue = "") String query,
            @RequestParam(required = false) ContactType type) {
        return ResponseEntity.ok(contactService.searchForDropdown(query, type));
    }
}

