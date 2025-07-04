package com.nextgenmanager.nextgenmanager.purchase.model;

import com.nextgenmanager.nextgenmanager.contact.model.Contact;
import com.nextgenmanager.nextgenmanager.sales.model.SalesOrder;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.util.Date;
import java.util.List;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "purchaseOrder")
public class PurchaseOrder {


    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String purchaseOrderNumber;

    @ManyToOne
    private Contact vendor;

    private Date orderDate;
    private Date expectedDeliveryDate;

    @Enumerated(EnumType.STRING)
    private PurchaseOrderStatus status; // CREATED, PARTIALLY_RECEIVED, COMPLETED, CANCELLED

    @ManyToOne(optional = true)
    private SalesOrder salesOrder; // Optional link

    @OneToMany(mappedBy = "purchaseOrder", cascade = CascadeType.ALL)
    private List<PurchaseOrderItem> items;

    private String remarks;

    @CreationTimestamp
    private Date createdDate;

    @UpdateTimestamp
    private Date updatedDate;

    private Date deletedDate;
}
