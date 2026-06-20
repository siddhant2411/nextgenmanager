package com.nextgenmanager.nextgenmanager.accounting.gst.filing.controller;

import com.nextgenmanager.nextgenmanager.accounting.gst.filing.dto.GstReturnDto;
import com.nextgenmanager.nextgenmanager.accounting.gst.filing.service.GstReturnService;
import com.nextgenmanager.nextgenmanager.common.security.authorization.RequiresAccountingAccess;
import com.nextgenmanager.nextgenmanager.common.security.authorization.RequiresAccountsHead;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;

@RestController
@RequestMapping("/api/accounting/gst")
@RequiresAccountingAccess
@RequiredArgsConstructor
public class GstReturnController {

    private final GstReturnService gstReturnService;

    /** File GSTR-1 for a period (records a snapshot; does not lock). Head/admin only. */
    @PostMapping("/file/gstr1/{periodId}")
    @RequiresAccountsHead
    public ResponseEntity<GstReturnDto> fileGstr1(@PathVariable Long periodId, Principal principal) {
        return ResponseEntity.ok(gstReturnService.fileGstr1(periodId, principal.getName()));
    }

    /** File GSTR-3B for a period (records a snapshot and locks the period). Head/admin only. */
    @PostMapping("/file/gstr3b/{periodId}")
    @RequiresAccountsHead
    public ResponseEntity<GstReturnDto> fileGstr3b(@PathVariable Long periodId, Principal principal) {
        return ResponseEntity.ok(gstReturnService.fileGstr3b(periodId, principal.getName()));
    }

    @GetMapping("/returns")
    public ResponseEntity<List<GstReturnDto>> filings(@RequestParam Long fy) {
        return ResponseEntity.ok(gstReturnService.getFilings(fy));
    }

    @GetMapping("/returns/{id}")
    public ResponseEntity<GstReturnDto> getReturn(@PathVariable Long id) {
        return ResponseEntity.ok(gstReturnService.getReturn(id));
    }
}
