package com.nextgenmanager.nextgenmanager.Inventory.model;

import com.nextgenmanager.nextgenmanager.contact.model.Contact;
import com.nextgenmanager.nextgenmanager.purchase.model.PurchaseOrder;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDate;
import java.util.Date;
import java.util.List;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "goodsReceiptNote")
public class GoodsReceiptNote {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String grnNumber;

    private LocalDate grnDate;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "purchase_order_id")
    private PurchaseOrder purchaseOrder;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vendor_id")
    private Contact vendor;

    /**
     * Where this sits. A NOT NULL reference since V164 — previously a free-text string that
     * nothing constrained, so two spellings of one store were two warehouses.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "warehouse_id", nullable = false)
    private Warehouse warehouse;

    @Enumerated(EnumType.STRING)
    private GRNStatus status;

    private double totalAmount;

    private String remarks;

    private String createdBy;

    @CreationTimestamp
    private Date createdDate;

    @OneToMany(mappedBy = "goodsReceiptNote", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<GoodsReceiptItem> items;
}
