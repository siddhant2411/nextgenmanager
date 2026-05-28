package com.nextgenmanager.nextgenmanager.purchase.model;

import com.nextgenmanager.nextgenmanager.Inventory.model.GoodsReceiptNote;
import com.nextgenmanager.nextgenmanager.contact.model.Contact;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Entity
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Table(name = "debitNote")
public class DebitNote {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String debitNoteNumber;

    private LocalDate debitNoteDate;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "vendor_id")
    private Contact vendor;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "purchase_order_id")
    private PurchaseOrder purchaseOrder;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "grn_id")
    private GoodsReceiptNote grn;

    @Enumerated(EnumType.STRING)
    private ReturnReason returnReason = ReturnReason.OTHER;

    @Enumerated(EnumType.STRING)
    private DebitNoteStatus status = DebitNoteStatus.DRAFT;

    private String remarks;

    private double subtotal;
    private double totalGstAmount;
    private double totalAmount;

    private String createdBy;

    @CreationTimestamp
    private LocalDate createdDate;

    private LocalDate deletedDate;

    @OneToMany(mappedBy = "debitNote", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<DebitNoteItem> items = new ArrayList<>();
}
