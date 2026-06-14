package com.nextgenmanager.nextgenmanager.common.approval.controller;

import com.nextgenmanager.nextgenmanager.common.approval.dto.ApprovalActRequest;
import com.nextgenmanager.nextgenmanager.common.approval.dto.ApprovalPolicyDto;
import com.nextgenmanager.nextgenmanager.common.approval.dto.ApprovalRequestDto;
import com.nextgenmanager.nextgenmanager.common.approval.dto.ApprovalRuleCreateDto;
import com.nextgenmanager.nextgenmanager.common.approval.model.ApprovalStep;
import com.nextgenmanager.nextgenmanager.common.approval.repository.ApprovalStepRepository;
import com.nextgenmanager.nextgenmanager.common.approval.service.ApprovalEngine;
import com.nextgenmanager.nextgenmanager.common.approval.service.ApprovalPolicyService;
import com.nextgenmanager.nextgenmanager.bom.service.ResourceNotFoundException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequiredArgsConstructor
public class ApprovalController {

    private final ApprovalEngine approvalEngine;
    private final ApprovalPolicyService policyService;
    private final ApprovalStepRepository stepRepo;

    // ─── Approver inbox ────────────────────────────────────────────────────────

    /**
     * Returns all PENDING steps for the caller's roles.
     * The caller passes their role explicitly so a single inbox covers all roles they hold.
     */
    @GetMapping("/api/approvals/inbox")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<ApprovalRequestDto>> inbox(
            @RequestParam String role,
            Principal principal) {
        return ResponseEntity.ok(
                stepRepo.findPendingByRole(role).stream()
                        .map(s -> approvalEngine.getRequest(
                                s.getRequest().getDocumentType(),
                                s.getRequest().getDocumentId()))
                        .collect(Collectors.toList()));
    }

    @PostMapping("/api/approvals/steps/{stepId}/act")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> act(@PathVariable Long stepId,
                                     @Valid @RequestBody ApprovalActRequest req,
                                     Authentication authentication) {
        // Authorization: the caller must actually hold the role this step is assigned to
        // (SUPER_ADMIN / ADMIN may always act). Without this, any authenticated user
        // could approve a step meant for another role.
        ApprovalStep step = stepRepo.findById(stepId)
                .orElseThrow(() -> new ResourceNotFoundException("Approval step not found: " + stepId));
        boolean authorized = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(a -> a.equals(step.getApproverRole())
                        || a.equals("ROLE_SUPER_ADMIN") || a.equals("ROLE_ADMIN"));
        if (!authorized) {
            throw new AccessDeniedException(
                    "You do not hold the role required to act on this approval step: " + step.getApproverRole());
        }
        approvalEngine.act(stepId, req.getApproved(), req.getComment(), authentication.getName());
        return ResponseEntity.ok().build();
    }

    @GetMapping("/api/approvals/requests")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApprovalRequestDto> getRequest(
            @RequestParam String documentType,
            @RequestParam Long documentId) {
        ApprovalRequestDto dto = approvalEngine.getRequest(documentType, documentId);
        if (dto == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(dto);
    }

    // ─── Policy admin (ACCOUNTS_ADMIN only) ────────────────────────────────────

    @GetMapping("/api/accounting/approval-policies")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN','ACCOUNTS_ADMIN')")
    public ResponseEntity<List<ApprovalPolicyDto>> listPolicies() {
        return ResponseEntity.ok(policyService.listAll());
    }

    @GetMapping("/api/accounting/approval-policies/{documentType}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN','ACCOUNTS_ADMIN')")
    public ResponseEntity<ApprovalPolicyDto> getPolicy(@PathVariable String documentType) {
        return ResponseEntity.ok(policyService.getByDocumentType(documentType));
    }

    @PutMapping("/api/accounting/approval-policies/{documentType}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN','ACCOUNTS_ADMIN')")
    public ResponseEntity<ApprovalPolicyDto> upsertPolicy(
            @PathVariable String documentType,
            @RequestParam boolean enabled) {
        return ResponseEntity.ok(policyService.createOrUpdate(documentType, enabled));
    }

    @PostMapping("/api/accounting/approval-policies/{documentType}/rules")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN','ACCOUNTS_ADMIN')")
    public ResponseEntity<ApprovalPolicyDto> addRule(
            @PathVariable String documentType,
            @Valid @RequestBody ApprovalRuleCreateDto dto) {
        return ResponseEntity.ok(policyService.addRule(documentType, dto));
    }

    @DeleteMapping("/api/accounting/approval-policies/rules/{ruleId}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN','ACCOUNTS_ADMIN')")
    public ResponseEntity<Void> deleteRule(@PathVariable Long ruleId) {
        policyService.deleteRule(ruleId);
        return ResponseEntity.noContent().build();
    }
}
