package com.nextgenmanager.nextgenmanager.sales.controller;

import com.nextgenmanager.nextgenmanager.sales.dto.DeliveryNoteCreateDto;
import com.nextgenmanager.nextgenmanager.sales.dto.DeliveryNoteDto;
import com.nextgenmanager.nextgenmanager.sales.service.DeliveryNoteService;
import com.nextgenmanager.nextgenmanager.sales.service.DeliveryNotePdfService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import com.nextgenmanager.nextgenmanager.common.security.authorization.RequiresSalesAccess;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/delivery-notes")
@RequiresSalesAccess
@RequiredArgsConstructor
public class DeliveryNoteController {

    private final DeliveryNoteService deliveryNoteService;
    private final DeliveryNotePdfService pdfService;

    @GetMapping("/{id}/pdf")
    public ResponseEntity<byte[]> downloadPdf(@PathVariable Long id) {
        byte[] pdf = pdfService.generatePdf(id);
        DeliveryNoteDto dto = deliveryNoteService.getDeliveryNoteById(id);
        String filename = "Challan_" + dto.getDeliveryNoteNo().replace("/", "_") + ".pdf";
        
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .body(pdf);
    }

    @PostMapping
    public ResponseEntity<DeliveryNoteDto> createDeliveryNote(@RequestBody DeliveryNoteCreateDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(deliveryNoteService.createDeliveryNote(dto));
    }

    @GetMapping("/{id}")
    public ResponseEntity<DeliveryNoteDto> getDeliveryNote(@PathVariable Long id) {
        return ResponseEntity.ok(deliveryNoteService.getDeliveryNoteById(id));
    }

    @GetMapping
    public ResponseEntity<Page<DeliveryNoteDto>> getAllDeliveryNotes(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "deliveryDate") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir,
            @RequestParam(required = false) Long salesOrderId
    ) {
        return ResponseEntity.ok(deliveryNoteService.getAllDeliveryNotes(page, size, sortBy, sortDir, salesOrderId));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteDeliveryNote(@PathVariable Long id) {
        deliveryNoteService.deleteDeliveryNote(id);
        return ResponseEntity.noContent().build();
    }
}
