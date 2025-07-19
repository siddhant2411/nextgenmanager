package com.nextgenmanager.nextgenmanager.Inventory.model;


import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.nextgenmanager.nextgenmanager.items.model.InventoryItem;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.util.Date;
import java.util.List;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "inventoryProcurementOrder")
public class InventoryProcurementOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "procurementRequestId", referencedColumnName = "id")
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    private InventoryRequest inventoryRequest;

    @OneToMany(cascade = CascadeType.ALL)
    @JoinColumn(name = "procurementOrderId") // Add this FK in InventoryInstance table
    private List<InventoryInstance> pendingInventoryList;

    @Enumerated(EnumType.STRING)
    private ProcurementDecision procurementDecision = ProcurementDecision.UNDECIDED;

    @Enumerated(EnumType.STRING)
    private InventoryProcurementStatus inventoryProcurementStatus = InventoryProcurementStatus.CREATED;

    private Long orderId;

    private String createdBy;

    @ManyToOne
    @JoinColumn(name = "inventoryItemProcurementRequest", referencedColumnName = "inventoryItemId")
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    private InventoryItem inventoryItem;


    @CreationTimestamp
    @Temporal(TemporalType.TIMESTAMP)
    @Column(updatable = false)
    private Date creationDate;
}
