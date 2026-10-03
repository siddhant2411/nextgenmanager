package com.nextgenmanager.nextgenmanager.packaging.controller;

import com.nextgenmanager.nextgenmanager.packaging.dto.*;
import com.nextgenmanager.nextgenmanager.packaging.model.PackingSlipStatus;
import com.nextgenmanager.nextgenmanager.packaging.service.PackingSlipPdfService;
import com.nextgenmanager.nextgenmanager.packaging.service.PackingSlipService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/packing-slip")
@RequiredArgsConstructor
public class PackingSlipController {

    private final PackingSlipService packingSlipService;
    private final PackingSlipPdfService pdfService;

    @GetMapping
    public ResponseEntity<List<PackingSlipDto>> list(
            @RequestParam(required = false) PackingSlipStatus status,
            @RequestParam(required = false) Long salesOrderId) {
        return ResponseEntity.ok(packingSlipService.list(status, salesOrderId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<PackingSlipDto> get(@PathVariable Long id) {
        return ResponseEntity.ok(packingSlipService.get(id));
    }

    @PostMapping
    public ResponseEntity<PackingSlipDto> create(@RequestBody PackingSlipCreateRequest request) {
        return ResponseEntity.ok(packingSlipService.createFromPickList(request));
    }

    @PostMapping("/{id}/boxes")
    public ResponseEntity<PackageBoxDto> addBox(
            @PathVariable Long id, @RequestBody PackageBoxCreateRequest request) {
        return ResponseEntity.ok(packingSlipService.addBox(id, request));
    }

    @PostMapping("/{id}/pack")
    public ResponseEntity<PackingSlipDto> pack(@PathVariable Long id) {
        return ResponseEntity.ok(packingSlipService.pack(id));
    }

    @PostMapping("/{id}/close")
    public ResponseEntity<PackingSlipDto> close(@PathVariable Long id) {
        return ResponseEntity.ok(packingSlipService.close(id));
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<Void> cancel(@PathVariable Long id) {
        packingSlipService.cancel(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/pdf")
    public ResponseEntity<byte[]> downloadPdf(@PathVariable Long id) {
        byte[] pdf = pdfService.generatePdf(id);
        PackingSlipDto dto = packingSlipService.get(id);
        String filename = "PackingList_" + dto.slipNumber().replace("/", "_") + ".pdf";

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .body(pdf);
    }
}
