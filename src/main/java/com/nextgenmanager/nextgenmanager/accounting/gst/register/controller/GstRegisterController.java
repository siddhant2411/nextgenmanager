package com.nextgenmanager.nextgenmanager.accounting.gst.register.controller;

import com.nextgenmanager.nextgenmanager.accounting.gst.register.dto.InwardRegisterDto;
import com.nextgenmanager.nextgenmanager.accounting.gst.register.dto.OutwardRegisterDto;
import com.nextgenmanager.nextgenmanager.accounting.gst.register.service.GstRegisterService;
import com.nextgenmanager.nextgenmanager.common.security.authorization.RequiresAccountingAccess;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/accounting/gst/register")
@RequiresAccountingAccess
@RequiredArgsConstructor
public class GstRegisterController {

    private static final String XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    private final GstRegisterService registerService;

    @GetMapping("/outward")
    public ResponseEntity<OutwardRegisterDto> outward(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(registerService.outwardRegister(from, to));
    }

    @GetMapping("/inward")
    public ResponseEntity<InwardRegisterDto> inward(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(registerService.inwardRegister(from, to));
    }

    @GetMapping("/outward/excel")
    public ResponseEntity<byte[]> outwardExcel(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        byte[] body = registerService.outwardExcel(registerService.outwardRegister(from, to));
        return excel(body, "Outward_Register_" + from + "_" + to + ".xlsx");
    }

    @GetMapping("/inward/excel")
    public ResponseEntity<byte[]> inwardExcel(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        byte[] body = registerService.inwardExcel(registerService.inwardRegister(from, to));
        return excel(body, "Inward_Register_" + from + "_" + to + ".xlsx");
    }

    private ResponseEntity<byte[]> excel(byte[] body, String fileName) {
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(XLSX))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + "\"")
                .body(body);
    }
}
