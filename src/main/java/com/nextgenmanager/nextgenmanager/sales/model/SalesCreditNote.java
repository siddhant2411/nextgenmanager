package com.nextgenmanager.nextgenmanager.sales.model;

import com.nextgenmanager.nextgenmanager.contact.model.Contact;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** A credit note issued to a customer for a sales return (sales-side mirror of DebitNote). */
@Entity
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Table(name = "salesCreditNote")
public class SalesCreditNote {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String creditNoteNumber;

    private LocalDate creditNoteDate;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "customer_id")
    private Contact customer;

    /** The original invoice being credited (optional — needed for the GSTR-1 credit-note section). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tax_invoice_id")
    private TaxInvoice taxInvoice;

    @Column(length = 40)
    private String reason;

    @Enumerated(EnumType.STRING)
    private SalesCreditNoteStatus status = SalesCreditNoteStatus.DRAFT;

    private String remarks;

    private double subtotal;
    private double totalGstAmount;
    private double totalAmount;

    private String createdBy;

    @CreationTimestamp
    private LocalDate createdDate;

    private LocalDate deletedDate;

    @OneToMany(mappedBy = "salesCreditNote", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<SalesCreditNoteItem> items = new ArrayList<>();
}
