package com.nextgenmanager.nextgenmanager.sales.model;


import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.nextgenmanager.nextgenmanager.contact.model.Contact;
import com.nextgenmanager.nextgenmanager.marketing.quotation.model.Quotation;
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
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Table(name = "salesOrder")

public class SalesOrder {

    @Id @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id;

    // — Voucher identification —
    @Column(unique = true, nullable = false)
    private String orderNumber;                     // e.g. “SO/2025/0001”
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private VoucherType voucherType = VoucherType.SALES_ORDER;

    // — Party & Reference —
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id", nullable = false)
    private Contact customer;

    @Column(nullable = false)
    private LocalDate orderDate;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "quotation_id")
    private Quotation quotation;

    // — Line items —
    @OneToMany(mappedBy = "salesOrder", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<SalesOrderItem> items = new ArrayList<>();

    // — Commercial summary —
    @Column(precision = 12, scale = 2) private BigDecimal subTotal;       // sum of line taxable values
    @Column(precision = 12, scale = 2) private BigDecimal discountAmount;

    @Column(precision = 12, scale = 2)
    private BigDecimal freightAndForwardingCharges = BigDecimal.ZERO;
    @Column(precision = 12, scale = 2) private BigDecimal taxableValue;   // subTotal – discount
    @Column(precision = 12, scale = 2) private BigDecimal cgstAmount;
    @Column(precision = 12, scale = 2) private BigDecimal sgstAmount;
    @Column(precision = 12, scale = 2) private BigDecimal igstAmount;
    @Column(precision = 12, scale = 2) private BigDecimal cessAmount;
    @Column(precision = 12, scale = 2) private BigDecimal roundOffAmount;
    @Column(precision = 12, scale = 2) private BigDecimal netAmount;     // final bill amount

    @Column(length = 200) private String paymentTerms;                    // e.g. “30% advance, balance on delivery”
    @Column(length = 200) private String incoterms;                      // e.g. “FOB, Indian Port”
    @Column(length = 20)  private String currency;                       // e.g. “INR”

    // — Logistics —
    @Column(length = 500) private String deliveryAddress;
    @Column(length = 100) private String dispatchThrough;                // e.g. “By Road”
    @Column(length = 50)  private String transportMode;                  // e.g. “Truck”
    @Column(length = 50)  private String ewayBillNumber;
    private LocalDate deliveryDate;

    @Column(length = 200) private String packagingInstructions;
    @Column(length = 100) private String shippingMethod;

    // — References —
    @Column(length = 50)  private String poNumber;
    private LocalDate poDate;
    @Column(length = 200) private String reference;                      // any other ref
    @Column(length = 500) private String remarks;

    // — Status & audit —
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SalesOrderStatus status = SalesOrderStatus.PENDING;



    @OneToMany(mappedBy = "salesOrder", cascade = CascadeType.ALL )
    private List<DeliveryNote> deliveryNotes;


    @OneToMany(mappedBy = "salesOrder", cascade = CascadeType.ALL)
    private List<TaxInvoice> taxInvoices;

    @CreationTimestamp
    @Column(updatable = false)
    private Date creationDate;

    @UpdateTimestamp
    private Date updatedDate;

    private Date deletedDate;
}
