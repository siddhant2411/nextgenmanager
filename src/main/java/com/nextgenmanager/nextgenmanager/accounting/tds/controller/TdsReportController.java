package com.nextgenmanager.nextgenmanager.accounting.tds.controller;

import com.nextgenmanager.nextgenmanager.accounting.tds.dto.TdsEntryDto;
import com.nextgenmanager.nextgenmanager.accounting.tds.service.TdsReportService;
import com.nextgenmanager.nextgenmanager.common.security.authorization.RequiresAccountingAccess;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/accounting/tds")
@RequiresAccountingAccess
@RequiredArgsConstructor
public class TdsReportController {

    private static final String XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    private final TdsReportService reportService;

    @GetMapping("/register")
    public ResponseEntity<List<TdsEntryDto>> register(@RequestParam String fy, @RequestParam String quarter) {
        return ResponseEntity.ok(reportService.register(fy, quarter));
    }

    @GetMapping("/26q/excel")
    public ResponseEntity<byte[]> export26Q(@RequestParam String fy, @RequestParam String quarter) {
        byte[] body = reportService.export26Q(fy, quarter);
        String fileName = "26Q_" + fy + "_" + quarter + ".xlsx";
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(XLSX))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + "\"")
                .body(body);
    }
}
