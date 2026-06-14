package com.nextgenmanager.nextgenmanager.accounting.voucher.controller;

import com.nextgenmanager.nextgenmanager.accounting.voucher.dto.*;
import com.nextgenmanager.nextgenmanager.accounting.voucher.model.VoucherStatus;
import com.nextgenmanager.nextgenmanager.accounting.voucher.model.VoucherType;
import com.nextgenmanager.nextgenmanager.accounting.voucher.repository.VoucherRepository;
import com.nextgenmanager.nextgenmanager.accounting.voucher.service.PostingService;
import com.nextgenmanager.nextgenmanager.common.security.authorization.RequiresAccountingAccess;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/accounting/vouchers")
@RequiresAccountingAccess
@RequiredArgsConstructor
public class VoucherController {

    private final PostingService postingService;
    private final VoucherRepository voucherRepo;

    // ─── Journal entry ─────────────────────────────────────────────────────────

    @PostMapping("/journal")
    public ResponseEntity<VoucherDto> postJournal(@Valid @RequestBody JournalVoucherRequest req,
                                                   java.security.Principal principal) {
        VoucherDraft draft = new VoucherDraft();
        draft.setVoucherType(VoucherType.JOURNAL);
        draft.setDate(req.getDate());
        draft.setNarration(req.getNarration());
        draft.setLines(req.getLines());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(postingService.post(draft, principal.getName()));
    }

    @PostMapping("/receipt")
    public ResponseEntity<VoucherDto> postReceipt(@Valid @RequestBody ReceiptVoucherRequest req,
                                                   java.security.Principal principal) {
        VoucherLineDraft dr = new VoucherLineDraft();
        dr.setLedgerAccountId(req.getBankOrCashAccountId());
        dr.setDrAmount(req.getAmount());

        VoucherLineDraft cr = new VoucherLineDraft();
        cr.setLedgerAccountId(req.getFromAccountId());
        cr.setCrAmount(req.getAmount());

        VoucherDraft draft = new VoucherDraft();
        draft.setVoucherType(VoucherType.RECEIPT);
        draft.setDate(req.getDate());
        draft.setNarration(req.getNarration());
        draft.setLines(List.of(dr, cr));
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(postingService.post(draft, principal.getName()));
    }

    @PostMapping("/payment")
    public ResponseEntity<VoucherDto> postPayment(@Valid @RequestBody PaymentVoucherRequest req,
                                                   java.security.Principal principal) {
        VoucherLineDraft dr = new VoucherLineDraft();
        dr.setLedgerAccountId(req.getToAccountId());
        dr.setDrAmount(req.getAmount());

        VoucherLineDraft cr = new VoucherLineDraft();
        cr.setLedgerAccountId(req.getBankOrCashAccountId());
        cr.setCrAmount(req.getAmount());

        VoucherDraft draft = new VoucherDraft();
        draft.setVoucherType(VoucherType.PAYMENT);
        draft.setDate(req.getDate());
        draft.setNarration(req.getNarration());
        draft.setLines(List.of(dr, cr));
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(postingService.post(draft, principal.getName()));
    }

    @PostMapping("/contra")
    public ResponseEntity<VoucherDto> postContra(@Valid @RequestBody ContraVoucherRequest req,
                                                  java.security.Principal principal) {
        VoucherLineDraft dr = new VoucherLineDraft();
        dr.setLedgerAccountId(req.getToAccountId());
        dr.setDrAmount(req.getAmount());

        VoucherLineDraft cr = new VoucherLineDraft();
        cr.setLedgerAccountId(req.getFromAccountId());
        cr.setCrAmount(req.getAmount());

        VoucherDraft draft = new VoucherDraft();
        draft.setVoucherType(VoucherType.CONTRA);
        draft.setDate(req.getDate());
        draft.setNarration(req.getNarration());
        draft.setLines(List.of(dr, cr));
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(postingService.post(draft, principal.getName()));
    }

    // ─── Reversal ──────────────────────────────────────────────────────────────

    @PostMapping("/{id}/reverse")
    public ResponseEntity<VoucherDto> reverse(@PathVariable Long id,
                                               @Valid @RequestBody ReverseVoucherRequest req,
                                               java.security.Principal principal) {
        return ResponseEntity.ok(postingService.reverse(id, req.getReason(), principal.getName()));
    }

    // ─── Queries ───────────────────────────────────────────────────────────────

    @GetMapping
    public ResponseEntity<List<VoucherListDto>> list(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) VoucherType type,
            @RequestParam(required = false) VoucherStatus status) {
        LocalDate effectiveFrom = from != null ? from : LocalDate.now().withDayOfYear(1);
        LocalDate effectiveTo   = to   != null ? to   : LocalDate.now();

        return ResponseEntity.ok(
                voucherRepo.findByDeletedDateIsNullAndDateBetweenOrderByDateAscVoucherNumberAsc(effectiveFrom, effectiveTo)
                        .stream()
                        .filter(v -> type   == null || v.getVoucherType() == type)
                        .filter(v -> status == null || v.getStatus() == status)
                        .map(v -> {
                            VoucherListDto dto = new VoucherListDto();
                            dto.setId(v.getId());
                            dto.setVoucherNumber(v.getVoucherNumber());
                            dto.setVoucherType(v.getVoucherType());
                            dto.setDate(v.getDate());
                            dto.setNarration(v.getNarration());
                            dto.setStatus(v.getStatus());
                            dto.setCreatedBy(v.getCreatedBy());
                            dto.setSourceDocType(v.getSourceDocType());
                            dto.setSourceDocId(v.getSourceDocId());
                            return dto;
                        })
                        .collect(Collectors.toList()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<VoucherDto> getById(@PathVariable Long id) {
        // Uses JOIN FETCH internally — no lazy loading issue
        return voucherRepo.findByIdWithLines(id)
                .map(v -> {
                    VoucherDto dto = new VoucherDto();
                    dto.setId(v.getId());
                    dto.setVoucherNumber(v.getVoucherNumber());
                    dto.setVoucherType(v.getVoucherType());
                    dto.setDate(v.getDate());
                    dto.setNarration(v.getNarration());
                    dto.setStatus(v.getStatus());
                    dto.setCreatedBy(v.getCreatedBy());
                    dto.setPeriodId(v.getPeriod() != null ? v.getPeriod().getId() : null);
                    dto.setSourceDocType(v.getSourceDocType());
                    dto.setSourceDocId(v.getSourceDocId());
                    dto.setReversalOfId(v.getReversalOf() != null ? v.getReversalOf().getId() : null);
                    dto.setReversedByVoucherId(v.getReversedByVoucher() != null ? v.getReversedByVoucher().getId() : null);
                    List<VoucherLineDto> lineDtos = v.getLines().stream().map(l -> {
                        VoucherLineDto ld = new VoucherLineDto();
                        ld.setId(l.getId());
                        ld.setLedgerAccountId(l.getLedgerAccount().getId());
                        ld.setLedgerAccountCode(l.getLedgerAccount().getCode());
                        ld.setLedgerAccountName(l.getLedgerAccount().getName());
                        ld.setDrAmount(l.getDrAmount());
                        ld.setCrAmount(l.getCrAmount());
                        ld.setNarration(l.getNarration());
                        return ld;
                    }).collect(Collectors.toList());
                    dto.setLines(lineDtos);
                    java.math.BigDecimal totalDr = lineDtos.stream()
                            .map(VoucherLineDto::getDrAmount).reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);
                    java.math.BigDecimal totalCr = lineDtos.stream()
                            .map(VoucherLineDto::getCrAmount).reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);
                    dto.setTotalDr(totalDr);
                    dto.setTotalCr(totalCr);
                    return ResponseEntity.ok(dto);
                })
                .orElse(ResponseEntity.notFound().build());
    }
}
