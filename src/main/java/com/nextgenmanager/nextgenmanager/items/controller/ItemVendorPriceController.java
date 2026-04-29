package com.nextgenmanager.nextgenmanager.items.controller;

import com.nextgenmanager.nextgenmanager.items.dto.ItemVendorPriceDTO;
import com.nextgenmanager.nextgenmanager.items.dto.ItemVendorPriceRequestDTO;
import com.nextgenmanager.nextgenmanager.items.model.ItemVendorPriceHistory;
import com.nextgenmanager.nextgenmanager.items.model.PriceType;
import com.nextgenmanager.nextgenmanager.items.service.ItemVendorPriceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/items/{itemId}/vendor-prices")
@Tag(name = "Item Vendor Prices", description = "Manage vendor purchase prices and job-work rates per item")
public class ItemVendorPriceController {

    @Autowired
    private ItemVendorPriceService service;

    /** GET /api/items/{itemId}/vendor-prices — all vendors for this item */
    @GetMapping
    @PreAuthorize("hasAnyRole('ROLE_ADMIN', 'ROLE_SUPER_ADMIN', 'ROLE_INVENTORY_ADMIN', 'ROLE_INVENTORY_USER', 'ROLE_PURCHASE_ADMIN', 'ROLE_PURCHASE_USER')")
    @Operation(summary = "List all vendor prices for an item (PURCHASE + JOB_WORK)")
    public ResponseEntity<List<ItemVendorPriceDTO>> getAll(@PathVariable int itemId) {
        return ResponseEntity.ok(service.getByItem(itemId));
    }

    /** GET /api/items/{itemId}/vendor-prices?priceType=PURCHASE */
    @GetMapping(params = "priceType")
    @PreAuthorize("hasAnyRole('ROLE_ADMIN', 'ROLE_SUPER_ADMIN', 'ROLE_INVENTORY_ADMIN', 'ROLE_INVENTORY_USER', 'ROLE_PURCHASE_ADMIN', 'ROLE_PURCHASE_USER')")
    @Operation(summary = "List vendor prices for an item filtered by priceType (PURCHASE or JOB_WORK)")
    public ResponseEntity<List<ItemVendorPriceDTO>> getByType(
            @PathVariable int itemId,
            @RequestParam PriceType priceType) {
        return ResponseEntity.ok(service.getByItemAndType(itemId, priceType));
    }

    /** GET /api/items/{itemId}/vendor-prices/{id} */
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ROLE_ADMIN', 'ROLE_SUPER_ADMIN', 'ROLE_INVENTORY_ADMIN', 'ROLE_INVENTORY_USER', 'ROLE_PURCHASE_ADMIN', 'ROLE_PURCHASE_USER')")
    public ResponseEntity<ItemVendorPriceDTO> getById(
            @PathVariable int itemId, @PathVariable Long id) {
        return ResponseEntity.ok(service.getById(id));
    }

    /** POST /api/items/{itemId}/vendor-prices */
    @PostMapping
    @PreAuthorize("hasAnyRole('ROLE_ADMIN', 'ROLE_SUPER_ADMIN')")
    @Operation(
        summary = "Add a vendor price for an item",
        description = "Set priceType=PURCHASE for buy prices; priceType=JOB_WORK for subcontract/job-work rates. " +
                      "Set isPreferredVendor=true to use this price in Make-or-Buy analysis automatically."
    )
    public ResponseEntity<ItemVendorPriceDTO> create(
            @PathVariable int itemId,
            @Valid @RequestBody ItemVendorPriceRequestDTO request) {
        request.setInventoryItemId(itemId);
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(request));
    }

    /** PUT /api/items/{itemId}/vendor-prices/{id} */
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ROLE_ADMIN', 'ROLE_SUPER_ADMIN')")
    @Operation(summary = "Update price, lead time, validity or preferred flag")
    public ResponseEntity<ItemVendorPriceDTO> update(
            @PathVariable int itemId,
            @PathVariable Long id,
            @Valid @RequestBody ItemVendorPriceRequestDTO request) {
        request.setInventoryItemId(itemId);
        return ResponseEntity.ok(service.update(id, request));
    }

    /** PATCH /api/items/{itemId}/vendor-prices/{id}/set-preferred */
    @PatchMapping("/{id}/set-preferred")
    @PreAuthorize("hasAnyRole('ROLE_ADMIN', 'ROLE_SUPER_ADMIN')")
    @Operation(
        summary = "Mark this vendor as the preferred source for Make-or-Buy analysis",
        description = "Clears the preferred flag on any previously preferred entry for the same item+priceType."
    )
    public ResponseEntity<ItemVendorPriceDTO> setPreferred(
            @PathVariable int itemId, @PathVariable Long id) {
        return ResponseEntity.ok(service.setPreferred(id));
    }

    /** GET /api/items/{itemId}/vendor-prices/{id}/history — price change log, newest first */
    @GetMapping("/{id}/history")
    @PreAuthorize("hasAnyRole('ROLE_ADMIN', 'ROLE_SUPER_ADMIN', 'ROLE_INVENTORY_ADMIN', 'ROLE_INVENTORY_USER', 'ROLE_PURCHASE_ADMIN', 'ROLE_PURCHASE_USER')")
    @Operation(summary = "Price change history for a vendor-price entry (newest first)")
    public ResponseEntity<List<ItemVendorPriceHistory>> getHistory(
            @PathVariable int itemId, @PathVariable Long id) {
        return ResponseEntity.ok(service.getHistory(id));
    }

    /** DELETE /api/items/{itemId}/vendor-prices/{id} */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ROLE_ADMIN', 'ROLE_SUPER_ADMIN')")
    public ResponseEntity<Void> delete(
            @PathVariable int itemId, @PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
