package com.nextgenmanager.nextgenmanager.Inventory.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonManagedReference;
import com.nextgenmanager.nextgenmanager.items.model.InventoryItem;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;


@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "inventoryRequest")
public class InventoryRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id;

    private String referenceNumber;

    @Enumerated(EnumType.STRING)
    private InventoryRequestSource requestSource;

    private Long sourceId;
    @OneToMany(mappedBy = "inventoryRequest", cascade = CascadeType.ALL)
    @JsonManagedReference
    private List<InventoryInstance> requestedInstances = new ArrayList<>();

    @Temporal(TemporalType.TIMESTAMP)
    private Date requestedDate;

    @Column(name = "approvalStatus")
    @Enumerated(EnumType.STRING)
    private InventoryApprovalStatus approvalStatus = InventoryApprovalStatus.PENDING;


    @ManyToOne
    @JoinColumn(name = "inventoryItemRequest", referencedColumnName = "inventoryItemId")
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    private  InventoryItem inventoryItem;

    private String requestedBy;

    private String requestRemarks;

}
