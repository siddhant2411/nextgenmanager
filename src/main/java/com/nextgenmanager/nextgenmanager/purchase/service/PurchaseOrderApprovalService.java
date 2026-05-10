package com.nextgenmanager.nextgenmanager.purchase.service;

import com.nextgenmanager.nextgenmanager.purchase.exception.InvalidPurchaseOrderStateException;
import com.nextgenmanager.nextgenmanager.purchase.model.PurchaseOrder;
import com.nextgenmanager.nextgenmanager.purchase.model.PurchaseOrderApprovalRule;
import com.nextgenmanager.nextgenmanager.purchase.model.PurchaseOrderApprovalStatus;
import com.nextgenmanager.nextgenmanager.purchase.repository.PurchaseOrderApprovalRuleRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

@Service
public class PurchaseOrderApprovalService {

    private final PurchaseOrderApprovalRuleRepository ruleRepo;

    public PurchaseOrderApprovalService(PurchaseOrderApprovalRuleRepository ruleRepo) {
        this.ruleRepo = ruleRepo;
    }

    public void approve(PurchaseOrder po) {
        if (po.getApprovalStatus() != PurchaseOrderApprovalStatus.PENDING_APPROVAL) {
            throw new InvalidPurchaseOrderStateException(
                    "PO " + po.getPurchaseOrderNumber() + " is not pending approval.");
        }
        assertCurrentUserHasRequiredRole(po.getGrandTotal());

        po.setApprovalStatus(PurchaseOrderApprovalStatus.APPROVED);
        po.setApprovedBy(currentUsername());
        po.setApprovedDate(new Date());
        po.setRejectionReason(null);
    }

    public void reject(PurchaseOrder po, String reason) {
        if (po.getApprovalStatus() != PurchaseOrderApprovalStatus.PENDING_APPROVAL) {
            throw new InvalidPurchaseOrderStateException(
                    "PO " + po.getPurchaseOrderNumber() + " is not pending approval.");
        }
        assertCurrentUserHasRequiredRole(po.getGrandTotal());

        po.setApprovalStatus(PurchaseOrderApprovalStatus.REJECTED);
        po.setRejectionReason(reason);
    }

    /** Finds the matching rule for this amount and checks current user has the required role. */
    private void assertCurrentUserHasRequiredRole(BigDecimal amount) {
        List<PurchaseOrderApprovalRule> rules = ruleRepo.findByActiveTrue();
        String requiredRole = rules.stream()
                .filter(r -> isAmountInRange(amount, r))
                .map(PurchaseOrderApprovalRule::getRequiredRole)
                .findFirst()
                .orElse("ROLE_SUPER_ADMIN");

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        boolean hasRole = auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals(requiredRole)
                        || a.getAuthority().equals("ROLE_SUPER_ADMIN"));

        if (!hasRole) {
            throw new InvalidPurchaseOrderStateException(
                    "Insufficient authority. Required: " + requiredRole);
        }
    }

    private boolean isAmountInRange(BigDecimal amount, PurchaseOrderApprovalRule rule) {
        if (amount == null) amount = BigDecimal.ZERO;
        boolean aboveMin = amount.compareTo(rule.getMinAmount()) >= 0;
        boolean belowMax = rule.getMaxAmount() == null
                || amount.compareTo(rule.getMaxAmount()) <= 0;
        return aboveMin && belowMax;
    }

    private String currentUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null ? auth.getName() : "system";
    }
}
