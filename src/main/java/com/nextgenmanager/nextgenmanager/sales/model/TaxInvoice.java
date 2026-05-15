package com.nextgenmanager.nextgenmanager.sales.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "taxInvoice")
public class TaxInvoice {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id;

    @Column(unique = true, nullable = false, length = 50)
    private String invoiceNumber;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "salesorder_id")
    @JsonIgnoreProperties({"deliveryNotes", "taxInvoices", "items"})
    private SalesOrder salesOrder;

    @Column(nullable = false)
    private LocalDate invoiceDate;

    private LocalDate dueDate;

    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    private TaxInvoiceStatus status = TaxInvoiceStatus.DRAFT;

    @Column(precision = 12, scale = 2) private BigDecimal subTotal;
    @Column(precision = 12, scale = 2) private BigDecimal taxableValue;
    @Column(precision = 12, scale = 2) private BigDecimal cgstAmount;
    @Column(precision = 12, scale = 2) private BigDecimal sgstAmount;
    @Column(precision = 12, scale = 2) private BigDecimal igstAmount;
    @Column(precision = 12, scale = 2) private BigDecimal totalPayableAmount;
    @Column(precision = 12, scale = 2) private BigDecimal paidAmount = BigDecimal.ZERO;

    @OneToMany(mappedBy = "taxInvoice", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<InvoiceItem> items = new ArrayList<>();

    @CreationTimestamp
    @Column(updatable = false)
    private Date creationDate;

    @UpdateTimestamp
    private Date updatedDate;

    private Date deletedDate;
}
