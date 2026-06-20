package com.nextgenmanager.nextgenmanager.accounting.gst.hsn.controller;

import com.nextgenmanager.nextgenmanager.accounting.gst.hsn.dto.HsnSummaryDto;
import com.nextgenmanager.nextgenmanager.accounting.gst.hsn.service.HsnSummaryService;
import com.nextgenmanager.nextgenmanager.common.security.authorization.RequiresAccountingAccess;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/accounting/gst/hsn-summary")
@RequiresAccountingAccess
@RequiredArgsConstructor
public class HsnSummaryController {

    private static final String XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    private final HsnSummaryService hsnSummaryService;

    @GetMapping
    public ResponseEntity<HsnSummaryDto> summary(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(hsnSummaryService.summary(from, to));
    }

    @GetMapping("/excel")
    public ResponseEntity<byte[]> excel(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        byte[] body = hsnSummaryService.toExcel(hsnSummaryService.summary(from, to));
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(XLSX))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"HSN_Summary_" + from + "_" + to + ".xlsx\"")
                .body(body);
    }
}
