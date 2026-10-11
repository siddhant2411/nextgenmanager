package com.nextgenmanager.nextgenmanager.dashboard.service;

import com.nextgenmanager.nextgenmanager.accounting.voucher.model.Voucher;
import com.nextgenmanager.nextgenmanager.accounting.voucher.repository.VoucherRepository;
import com.nextgenmanager.nextgenmanager.common.approval.model.ApprovalRequest;
import com.nextgenmanager.nextgenmanager.common.approval.model.ApprovalStep;
import com.nextgenmanager.nextgenmanager.common.approval.repository.ApprovalStepRepository;
import com.nextgenmanager.nextgenmanager.dashboard.dto.PendingApprovalDto;
import com.nextgenmanager.nextgenmanager.purchase.model.PurchaseOrder;
import com.nextgenmanager.nextgenmanager.purchase.model.PurchaseOrderApprovalStatus;
import com.nextgenmanager.nextgenmanager.purchase.repository.PurchaseOrderRepository;
import com.nextgenmanager.nextgenmanager.purchase.requisition.model.PurchaseRequisition;
import com.nextgenmanager.nextgenmanager.purchase.requisition.model.PurchaseRequisitionApprovalStatus;
import com.nextgenmanager.nextgenmanager.purchase.requisition.repository.PurchaseRequisitionRepository;
import com.nextgenmanager.nextgenmanager.purchase.requisition.service.PurchaseRequisitionApprovalService;
import com.nextgenmanager.nextgenmanager.purchase.service.PurchaseOrderApprovalService;
import com.nextgenmanager.nextgenmanager.sales.model.SalesOrder;
import com.nextgenmanager.nextgenmanager.sales.model.SalesOrderApprovalStatus;
import com.nextgenmanager.nextgenmanager.sales.repository.SalesOrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Set;

/**
 * The approvals a given user can act on, across the three mechanisms that hold them.
 *
 * Purchase orders and requisitions are approved by amount band, sales orders by the sales-admin
 * gate, vouchers by the generic approval engine. Each rule here mirrors the check its own approve
 * endpoint makes, so nothing is listed that the caller would be refused on.
 */
@Service
@RequiredArgsConstructor
public class MyApprovalsService {

    private static final String SUPER_ADMIN = "ROLE_SUPER_ADMIN";
    private static final String ADMIN = "ROLE_ADMIN";
    private static final String PURCHASE_ADMIN = "ROLE_PURCHASE_ADMIN";
    private static final String SALES_ADMIN = "ROLE_SALES_ADMIN";

    private final PurchaseOrderRepository purchaseOrderRepository;
    private final PurchaseOrderApprovalService purchaseOrderApprovalService;
    private final PurchaseRequisitionRepository purchaseRequisitionRepository;
    private final PurchaseRequisitionApprovalService purchaseRequisitionApprovalService;
    private final SalesOrderRepository salesOrderRepository;
    private final ApprovalStepRepository approvalStepRepository;
    private final VoucherRepository voucherRepository;

    /** Oldest first: the document that has waited longest is the one to look at. */
    @Transactional(readOnly = true)
    public List<PendingApprovalDto> pendingFor(Collection<String> authorities) {
        Set<String> roles = Set.copyOf(authorities);
        boolean admin = roles.contains(SUPER_ADMIN) || roles.contains(ADMIN);

        List<PendingApprovalDto> out = new ArrayList<>();
        if (admin || roles.contains(PURCHASE_ADMIN)) {
            addPurchaseOrders(out, roles, admin);
            addPurchaseRequisitions(out, roles, admin);
        }
        if (admin || roles.contains(SALES_ADMIN)) {
            addSalesOrders(out);
        }
        addVouchers(out, roles, admin);

        out.sort(Comparator.comparing(PendingApprovalDto::getDate,
                Comparator.nullsLast(Comparator.naturalOrder())));
        return out;
    }

    private void addPurchaseOrders(List<PendingApprovalDto> out, Set<String> roles, boolean admin) {
        for (PurchaseOrder po : purchaseOrderRepository.findByApprovalStatusAndDeletedDateIsNull(
                PurchaseOrderApprovalStatus.PENDING_APPROVAL, Pageable.unpaged())) {
            String required = purchaseOrderApprovalService.requiredRoleFor(po.getGrandTotal());
            if (!admin && !roles.contains(required)) continue;
            out.add(new PendingApprovalDto("PURCHASE_ORDER", po.getId(), po.getPurchaseOrderNumber(),
                    po.getVendor() != null ? po.getVendor().getCompanyName() : null,
                    po.getGrandTotal(), toLocalDate(po.getOrderDate()), required));
        }
    }

    private void addPurchaseRequisitions(List<PendingApprovalDto> out, Set<String> roles, boolean admin) {
        for (PurchaseRequisition pr : purchaseRequisitionRepository.findByApprovalStatusAndDeletedDateIsNull(
                PurchaseRequisitionApprovalStatus.PENDING_APPROVAL, Pageable.unpaged())) {
            String required = purchaseRequisitionApprovalService.requiredRoleFor(pr.getTotalEstimatedAmount());
            if (!admin && !roles.contains(required)) continue;
            out.add(new PendingApprovalDto("PURCHASE_REQUISITION", pr.getId(), pr.getPrNumber(),
                    pr.getRequestedBy(), pr.getTotalEstimatedAmount(),
                    toLocalDate(pr.getRequestDate()), required));
        }
    }

    private void addSalesOrders(List<PendingApprovalDto> out) {
        Specification<SalesOrder> pending = (root, query, cb) -> cb.and(
                cb.isNull(root.get("deletedDate")),
                cb.equal(root.get("approvalStatus"), SalesOrderApprovalStatus.PENDING_APPROVAL));
        for (SalesOrder so : salesOrderRepository.findAll(pending)) {
            out.add(new PendingApprovalDto("SALES_ORDER", so.getId(), so.getOrderNumber(),
                    so.getCustomer() != null ? so.getCustomer().getCompanyName() : null,
                    so.getNetAmount(), so.getOrderDate(), SALES_ADMIN));
        }
    }

    private void addVouchers(List<PendingApprovalDto> out, Set<String> roles, boolean admin) {
        // An admin may act on any step, so an admin is asked about every role that has one waiting.
        Collection<String> askFor = admin ? approvalStepRepository.findPendingRoles() : roles;
        for (String role : askFor) {
            for (ApprovalStep step : approvalStepRepository.findPendingByRole(role)) {
                ApprovalRequest request = step.getRequest();
                Voucher voucher = voucherRepository.findById(request.getDocumentId()).orElse(null);
                out.add(new PendingApprovalDto("VOUCHER", request.getDocumentId(),
                        voucher != null ? voucher.getVoucherNumber() : "#" + request.getDocumentId(),
                        request.getRequestedBy(), null,
                        toLocalDate(request.getRequestedAt()), step.getApproverRole()));
            }
        }
    }

    // java.sql.Date refuses toInstant(), and Hibernate hands those back for DATE columns.
    private static LocalDate toLocalDate(Date date) {
        return date == null ? null
                : Instant.ofEpochMilli(date.getTime()).atZone(ZoneId.systemDefault()).toLocalDate();
    }
}
