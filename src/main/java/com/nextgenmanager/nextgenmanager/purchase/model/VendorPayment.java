package com.nextgenmanager.nextgenmanager.purchase.model;

import com.nextgenmanager.nextgenmanager.sales.model.PaymentMode;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Date;

@Entity
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Table(name = "vendorPayment")
public class VendorPayment {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vendorInvoice_id", nullable = false)
    private VendorInvoice vendorInvoice;

    @Column(nullable = false)
    private LocalDate paymentDate;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentMode paymentMode;

    @Column(length = 100)
    private String referenceNumber;

    @Column(length = 500)
    private String notes;

    // ── TDS withheld on this payment (Phase 4). The vendor is settled at `amount`;
    //    actual bank outflow = amount − tdsAmount. Section is referenced by code (loose coupling). ──

    @Column(length = 20)
    private String tdsSectionCode;

    @Column(precision = 6, scale = 3)
    private BigDecimal tdsRate;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal tdsAmount = BigDecimal.ZERO;

    @Column(length = 100)
    private String createdBy;

    @CreationTimestamp
    @Column(updatable = false)
    private Date creationDate;

    @UpdateTimestamp
    private Date updatedDate;
}
