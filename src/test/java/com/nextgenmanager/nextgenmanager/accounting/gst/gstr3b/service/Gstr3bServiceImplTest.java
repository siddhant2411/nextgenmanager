package com.nextgenmanager.nextgenmanager.accounting.gst.gstr3b.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nextgenmanager.nextgenmanager.accounting.gst.GstSupport;
import com.nextgenmanager.nextgenmanager.accounting.gst.gstr3b.dto.Gstr3bDto;
import com.nextgenmanager.nextgenmanager.accounting.gst.register.dto.*;
import com.nextgenmanager.nextgenmanager.accounting.gst.register.service.GstRegisterService;
import com.nextgenmanager.nextgenmanager.accounting.voucher.repository.VoucherLineRepository;
import com.nextgenmanager.nextgenmanager.company.repository.CompanyDetailsRepository;
import com.nextgenmanager.nextgenmanager.purchase.service.GstResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class Gstr3bServiceImplTest {

    @Mock private GstRegisterService registerService;
    @Mock private VoucherLineRepository voucherLineRepo;
    @Mock private GstResolver gstResolver;
    @Mock private CompanyDetailsRepository companyRepo;

    private Gstr3bServiceImpl service;

    private static final LocalDate FROM = LocalDate.of(2025, 5, 1);
    private static final LocalDate TO = LocalDate.of(2025, 5, 31);
    private static final List<String> OUTPUT_CODES = List.of("9010", "9011", "9012", "9013");
    private static final List<String> INPUT_CODES = List.of("6020", "6021", "6022", "6023");

    @BeforeEach
    void setUp() {
        GstSupport gstSupport = new GstSupport(gstResolver, companyRepo);
        service = new Gstr3bServiceImpl(registerService, voucherLineRepo, new ObjectMapper(), gstSupport);
        lenient().when(companyRepo.findAll()).thenReturn(List.of());

        // One intra-state sales invoice: taxable 1000, CGST 90, SGST 90.
        OutwardRegisterRow inv = new OutwardRegisterRow("INV", "INV-1", FROM, "24XXX", "Cust", "24",
                new BigDecimal("1000.00"), new BigDecimal("18"),
                new BigDecimal("90.00"), new BigDecimal("90.00"), BigDecimal.ZERO, BigDecimal.ZERO,
                new BigDecimal("1180.00"));
        when(registerService.outwardRegister(FROM, TO))
                .thenReturn(new OutwardRegisterDto(FROM, TO, List.of(inv), GstTotals.zero()));

        // One inter-state purchase: taxable 1000, IGST 180, eligible ITC.
        InwardRegisterRow bill = new InwardRegisterRow("BILL", "B-1", FROM, "27YYY", "Vend", "27",
                new BigDecimal("1000.00"), new BigDecimal("18"),
                BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("180.00"), BigDecimal.ZERO,
                new BigDecimal("1180.00"), true, false);
        when(registerService.inwardRegister(FROM, TO))
                .thenReturn(new InwardRegisterDto(FROM, TO, List.of(bill), GstTotals.zero()));
    }

    private Object[] mv(String code, String dr, String cr) {
        return new Object[]{code, new BigDecimal(dr), new BigDecimal(cr)};
    }

    @Test
    void outwardTaxableAndItc_tieToLedgerMovement() {
        // Output ledger: CGST 90 + SGST 90 credited = 180 net credit
        when(voucherLineRepo.movementByCodeInRange(FROM, TO, OUTPUT_CODES)).thenReturn(List.<Object[]>of(
                mv("9010", "0.00", "90.00"), mv("9011", "0.00", "90.00")));
        // Input ledger: IGST 180 debited = 180 net debit
        when(voucherLineRepo.movementByCodeInRange(FROM, TO, INPUT_CODES)).thenReturn(List.<Object[]>of(
                mv("6022", "180.00", "0.00")));

        Gstr3bDto g = service.build(FROM, TO);

        assertThat(g.outwardTaxable().cgst()).isEqualByComparingTo("90.00");
        assertThat(g.outwardTaxable().sgst()).isEqualByComparingTo("90.00");
        assertThat(g.itcAvailable().igst()).isEqualByComparingTo("180.00");

        assertThat(g.reconciliation().ledgerOutput()).isEqualByComparingTo("180.00");
        assertThat(g.reconciliation().returnOutput()).isEqualByComparingTo("180.00");
        assertThat(g.reconciliation().outputVariance()).isEqualByComparingTo("0.00");
        assertThat(g.reconciliation().ledgerItc()).isEqualByComparingTo("180.00");
        assertThat(g.reconciliation().returnItc()).isEqualByComparingTo("180.00");
        assertThat(g.reconciliation().tiesOut()).isTrue();

        // Net payable: output 180 − ITC 180 = 0 (IGST output 0 − IGST ITC 180 floored at 0)
        assertThat(g.netTaxPayable().cgst()).isEqualByComparingTo("90.00");
        assertThat(g.netTaxPayable().igst()).isEqualByComparingTo("0");
    }

    @Test
    void outputLedgerShort_flagsVariance_doesNotTie() {
        // Output ledger only carries 170 (a sales voucher failed to post the other 10)
        when(voucherLineRepo.movementByCodeInRange(FROM, TO, OUTPUT_CODES)).thenReturn(List.<Object[]>of(
                mv("9010", "0.00", "90.00"), mv("9011", "0.00", "80.00")));
        when(voucherLineRepo.movementByCodeInRange(FROM, TO, INPUT_CODES)).thenReturn(List.<Object[]>of(
                mv("6022", "180.00", "0.00")));

        Gstr3bDto g = service.build(FROM, TO);

        assertThat(g.reconciliation().ledgerOutput()).isEqualByComparingTo("170.00");
        assertThat(g.reconciliation().returnOutput()).isEqualByComparingTo("180.00");
        assertThat(g.reconciliation().outputVariance()).isEqualByComparingTo("-10.00");
        assertThat(g.reconciliation().tiesOut()).isFalse();
    }
}
