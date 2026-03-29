package com.nextgenmanager.nextgenmanager.Inventory.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Date;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "inventoryBookingApproval")
public class InventoryBookingApproval {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id;


    @ManyToOne
    @JoinColumn(name = "instanceRequestId", referencedColumnName = "id")
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    private InventoryRequest inventoryRequest;

    private String approvedBy;

    private Date approvalDate;


    @Column(name = "approvalStatus")
    @Enumerated(EnumType.STRING)
    private InventoryApprovalStatus approvalStatus = InventoryApprovalStatus.PENDING;

    private String approvalRemarks;

}
