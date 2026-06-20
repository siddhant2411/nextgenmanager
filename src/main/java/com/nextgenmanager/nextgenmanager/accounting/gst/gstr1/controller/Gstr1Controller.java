package com.nextgenmanager.nextgenmanager.accounting.gst.gstr1.controller;

import com.nextgenmanager.nextgenmanager.accounting.gst.gstr1.dto.Gstr1Dto;
import com.nextgenmanager.nextgenmanager.accounting.gst.gstr1.service.Gstr1Service;
import com.nextgenmanager.nextgenmanager.common.security.authorization.RequiresAccountingAccess;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/accounting/gst/gstr1")
@RequiresAccountingAccess
@RequiredArgsConstructor
public class Gstr1Controller {

    private static final String XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    private final Gstr1Service gstr1Service;

    @GetMapping
    public ResponseEntity<Gstr1Dto> preview(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(gstr1Service.build(from, to));
    }

    @GetMapping("/excel")
    public ResponseEntity<byte[]> excel(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        byte[] body = gstr1Service.toExcel(gstr1Service.build(from, to));
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(XLSX))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"GSTR1_" + from + "_" + to + ".xlsx\"")
                .body(body);
    }

    @GetMapping("/json")
    public ResponseEntity<byte[]> json(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        byte[] body = gstr1Service.toJson(gstr1Service.build(from, to));
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"GSTR1_" + from + "_" + to + ".json\"")
                .body(body);
    }
}
