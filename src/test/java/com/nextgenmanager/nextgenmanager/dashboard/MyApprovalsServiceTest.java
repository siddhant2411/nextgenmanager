package com.nextgenmanager.nextgenmanager.dashboard;

import com.nextgenmanager.nextgenmanager.accounting.voucher.repository.VoucherRepository;
import com.nextgenmanager.nextgenmanager.common.approval.model.ApprovalRequest;
import com.nextgenmanager.nextgenmanager.common.approval.model.ApprovalStep;
import com.nextgenmanager.nextgenmanager.common.approval.repository.ApprovalStepRepository;
import com.nextgenmanager.nextgenmanager.dashboard.dto.PendingApprovalDto;
import com.nextgenmanager.nextgenmanager.dashboard.service.MyApprovalsService;
import com.nextgenmanager.nextgenmanager.purchase.model.PurchaseOrder;
import com.nextgenmanager.nextgenmanager.purchase.model.PurchaseOrderApprovalStatus;
import com.nextgenmanager.nextgenmanager.purchase.repository.PurchaseOrderRepository;
import com.nextgenmanager.nextgenmanager.purchase.requisition.model.PurchaseRequisitionApprovalStatus;
import com.nextgenmanager.nextgenmanager.purchase.requisition.repository.PurchaseRequisitionRepository;
import com.nextgenmanager.nextgenmanager.purchase.requisition.service.PurchaseRequisitionApprovalService;
import com.nextgenmanager.nextgenmanager.purchase.service.PurchaseOrderApprovalService;
import com.nextgenmanager.nextgenmanager.sales.model.SalesOrder;
import com.nextgenmanager.nextgenmanager.sales.repository.SalesOrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MyApprovalsServiceTest {

    @Mock private PurchaseOrderRepository purchaseOrderRepository;
    @Mock private PurchaseOrderApprovalService purchaseOrderApprovalService;
    @Mock private PurchaseRequisitionRepository purchaseRequisitionRepository;
    @Mock private PurchaseRequisitionApprovalService purchaseRequisitionApprovalService;
    @Mock private SalesOrderRepository salesOrderRepository;
    @Mock private ApprovalStepRepository approvalStepRepository;
    @Mock private VoucherRepository voucherRepository;

    @InjectMocks private MyApprovalsService service;

    private static final BigDecimal SMALL = new BigDecimal("5000");
    private static final BigDecimal LARGE = new BigDecimal("900000");

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        // Two POs waiting: a small one in the purchase admin's band, a large one above it.
        PurchaseOrder small = po(1L, "PO-1", SMALL);
        PurchaseOrder large = po(2L, "PO-2", LARGE);
        when(purchaseOrderRepository.findByApprovalStatusAndDeletedDateIsNull(
                eq(PurchaseOrderApprovalStatus.PENDING_APPROVAL), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(small, large)));
        when(purchaseOrderApprovalService.requiredRoleFor(SMALL)).thenReturn("ROLE_PURCHASE_ADMIN");
        when(purchaseOrderApprovalService.requiredRoleFor(LARGE)).thenReturn("ROLE_SUPER_ADMIN");

        when(purchaseRequisitionRepository.findByApprovalStatusAndDeletedDateIsNull(
                eq(PurchaseRequisitionApprovalStatus.PENDING_APPROVAL), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        SalesOrder so = new SalesOrder();
        so.setId(7L);
        so.setOrderNumber("SO-7");
        so.setOrderDate(LocalDate.of(2026, 10, 1));
        when(salesOrderRepository.findAll(any(Specification.class))).thenReturn(List.of(so));

        ApprovalRequest request = mock(ApprovalRequest.class);
        when(request.getDocumentId()).thenReturn(31L);
        when(request.getRequestedAt()).thenReturn(new Date());
        ApprovalStep step = mock(ApprovalStep.class);
        when(step.getRequest()).thenReturn(request);
        when(step.getApproverRole()).thenReturn("ROLE_ACCOUNTS_HEAD");
        when(approvalStepRepository.findPendingRoles()).thenReturn(List.of("ROLE_ACCOUNTS_HEAD"));
        when(approvalStepRepository.findPendingByRole("ROLE_ACCOUNTS_HEAD")).thenReturn(List.of(step));
    }

    private static PurchaseOrder po(Long id, String number, BigDecimal total) {
        PurchaseOrder po = mock(PurchaseOrder.class);
        when(po.getId()).thenReturn(id);
        when(po.getPurchaseOrderNumber()).thenReturn(number);
        when(po.getGrandTotal()).thenReturn(total);
        return po;
    }

    private List<String> numbers(String... roles) {
        return service.pendingFor(List.of(roles)).stream().map(PendingApprovalDto::getNumber).toList();
    }

    @Test
    void adminSeesEveryPendingApproval() {
        assertThat(numbers("ROLE_ADMIN")).containsExactlyInAnyOrder("PO-1", "PO-2", "SO-7", "#31");
    }

    @Test
    void adminAndSuperAdminSeeTheSame() {
        assertThat(numbers("ROLE_ADMIN")).containsExactlyInAnyOrderElementsOf(numbers("ROLE_SUPER_ADMIN"));
    }

    @Test
    void purchaseAdminSeesOnlyPurchaseOrdersInTheirBand() {
        assertThat(numbers("ROLE_PURCHASE_ADMIN")).containsExactly("PO-1");
    }

    @Test
    void salesAdminSeesOnlySalesOrders() {
        assertThat(numbers("ROLE_SALES_ADMIN")).containsExactly("SO-7");
    }

    @Test
    void accountsHeadSeesOnlyTheirVoucherSteps() {
        assertThat(numbers("ROLE_ACCOUNTS_HEAD")).containsExactly("#31");
    }

    @Test
    void aUserWithNoApprovingRoleSeesNothingAndNoPurchaseOrSalesReadIsMade() {
        assertThat(numbers("ROLE_SALES_USER", "ROLE_PURCHASE_USER")).isEmpty();
        verify(purchaseOrderRepository, never()).findByApprovalStatusAndDeletedDateIsNull(any(), any());
        verify(salesOrderRepository, never()).findAll(any(Specification.class));
    }
}
