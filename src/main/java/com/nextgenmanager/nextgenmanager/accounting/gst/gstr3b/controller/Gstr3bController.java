package com.nextgenmanager.nextgenmanager.accounting.gst.gstr3b.controller;

import com.nextgenmanager.nextgenmanager.accounting.gst.gstr3b.dto.Gstr3bDto;
import com.nextgenmanager.nextgenmanager.accounting.gst.gstr3b.service.Gstr3bService;
import com.nextgenmanager.nextgenmanager.common.security.authorization.RequiresAccountingAccess;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/accounting/gst/gstr3b")
@RequiresAccountingAccess
@RequiredArgsConstructor
public class Gstr3bController {

    private static final String XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    private final Gstr3bService gstr3bService;

    @GetMapping
    public ResponseEntity<Gstr3bDto> preview(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(gstr3bService.build(from, to));
    }

    @GetMapping("/excel")
    public ResponseEntity<byte[]> excel(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        byte[] body = gstr3bService.toExcel(gstr3bService.build(from, to));
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(XLSX))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"GSTR3B_" + from + "_" + to + ".xlsx\"")
                .body(body);
    }

    @GetMapping("/json")
    public ResponseEntity<byte[]> json(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        byte[] body = gstr3bService.toJson(gstr3bService.build(from, to));
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"GSTR3B_" + from + "_" + to + ".json\"")
                .body(body);
    }
}
