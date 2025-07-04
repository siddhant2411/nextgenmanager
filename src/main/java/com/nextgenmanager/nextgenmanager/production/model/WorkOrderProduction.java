package com.nextgenmanager.nextgenmanager.production.model;


import com.fasterxml.jackson.annotation.JsonManagedReference;
import com.nextgenmanager.nextgenmanager.marketing.enquiry.model.EnquiredProducts;
import com.nextgenmanager.nextgenmanager.sales.model.SalesOrder;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "workOrderProduction")
public class WorkOrderProduction {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private int id;

    @ManyToOne(cascade = CascadeType.DETACH, optional = true)
    private SalesOrder salesOrder;

    @ManyToOne(cascade = CascadeType.DETACH)
    private WorkOrderProduction ParentWorkOrderProduction;

    @ManyToOne(cascade = CascadeType.DETACH, optional = false)
    private WorkOrderProductionTemplate workOrderProductionTemplate;

    @Enumerated(EnumType.STRING)  // Use STRING instead of ORDINAL for safety
    private WorkOrderStatus workOrderStatus;

    private  double quantity;

    private boolean isCreateChildItems=true;

    @Column(unique = true, nullable = false)
    private String workOrderNumber;

    @Column(precision = 10, scale = 1)
    private BigDecimal actualWorkHours;

    @Column(precision = 10, scale = 2)
    private BigDecimal actualCostOfLabour;

    @Column(precision = 10, scale = 2)
    private BigDecimal actualCostOfBom;

    @Column(precision = 10, scale = 2)
    private BigDecimal actualTotalCostOfWorkOrder;

    @OneToMany(mappedBy = "workOrderProduction", cascade = CascadeType.ALL, orphanRemoval = false)
    @JsonManagedReference
    private List<WorkOrderInventoryInstanceList> workOrderInventoryInstanceLists;


    @Column(precision = 10, scale = 2)
    private BigDecimal estimatedCostOfLabour;

    @Column(precision = 10, scale = 2)
    private BigDecimal estimatedCostOfBom;

    @Column(precision = 10, scale = 2)
    private BigDecimal overheadCostPercentage;

    @Column(precision = 10, scale = 2)
    private BigDecimal totalEstimatedCostOfWorkOrder;

    @Enumerated(EnumType.STRING)
    private WorkOrderSourceType sourceType;

    private String remarks;

    private Date dueDate;

    @CreationTimestamp
    @Column(updatable = false)
    private Date creationDate;

    @UpdateTimestamp
    private Date updatedDate;

    private Date deletedDate;
}
