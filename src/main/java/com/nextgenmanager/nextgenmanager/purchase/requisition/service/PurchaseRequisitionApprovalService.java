package com.nextgenmanager.nextgenmanager.purchase.requisition.service;

import com.nextgenmanager.nextgenmanager.purchase.requisition.exception.InvalidPurchaseRequisitionStateException;
import com.nextgenmanager.nextgenmanager.purchase.requisition.model.PurchaseRequisition;
import com.nextgenmanager.nextgenmanager.purchase.requisition.model.PurchaseRequisitionApprovalRule;
import com.nextgenmanager.nextgenmanager.purchase.requisition.model.PurchaseRequisitionApprovalStatus;
import com.nextgenmanager.nextgenmanager.purchase.requisition.repository.PurchaseRequisitionApprovalRuleRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

@Service
public class PurchaseRequisitionApprovalService {

    private final PurchaseRequisitionApprovalRuleRepository ruleRepo;

    public PurchaseRequisitionApprovalService(PurchaseRequisitionApprovalRuleRepository ruleRepo) {
        this.ruleRepo = ruleRepo;
    }

    public void approve(PurchaseRequisition pr) {
        if (pr.getApprovalStatus() != PurchaseRequisitionApprovalStatus.PENDING_APPROVAL) {
            throw new InvalidPurchaseRequisitionStateException(
                    "PR " + pr.getPrNumber() + " is not pending approval.");
        }
        assertCurrentUserHasRequiredRole(pr.getTotalEstimatedAmount());

        pr.setApprovalStatus(PurchaseRequisitionApprovalStatus.APPROVED);
        pr.setApprovedBy(currentUsername());
        pr.setApprovedDate(new Date());
        pr.setRejectionReason(null);
    }

    public void reject(PurchaseRequisition pr, String reason) {
        if (pr.getApprovalStatus() != PurchaseRequisitionApprovalStatus.PENDING_APPROVAL) {
            throw new InvalidPurchaseRequisitionStateException(
                    "PR " + pr.getPrNumber() + " is not pending approval.");
        }
        assertCurrentUserHasRequiredRole(pr.getTotalEstimatedAmount());

        pr.setApprovalStatus(PurchaseRequisitionApprovalStatus.REJECTED);
        pr.setRejectionReason(reason);
    }

    private void assertCurrentUserHasRequiredRole(BigDecimal amount) {
        List<PurchaseRequisitionApprovalRule> rules = ruleRepo.findByActiveTrue();
        String requiredRole = rules.stream()
                .filter(r -> isAmountInRange(amount, r))
                .map(PurchaseRequisitionApprovalRule::getRequiredRole)
                .findFirst()
                .orElse("ROLE_SUPER_ADMIN");

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        boolean hasRole = auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals(requiredRole)
                        || a.getAuthority().equals("ROLE_SUPER_ADMIN"));

        if (!hasRole) {
            throw new InvalidPurchaseRequisitionStateException(
                    "Insufficient authority. Required: " + requiredRole);
        }
    }

    private boolean isAmountInRange(BigDecimal amount, PurchaseRequisitionApprovalRule rule) {
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
