package com.nextgenmanager.nextgenmanager.production.helper;

import com.nextgenmanager.nextgenmanager.production.enums.WorkOrderEventType;
import com.nextgenmanager.nextgenmanager.production.model.WorkOrder;
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
@Table(name = "workOrderHistory")
public class WorkOrderHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // ---- Context ----
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "workOrderId", nullable = false)
    private WorkOrder workOrder;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private WorkOrderEventType eventType;

    // ---- What changed ----
    private String fieldName;

    private String oldValue;
    private String newValue;

    // ---- Who & when ----
    private String performedBy;   // username / system
    private Date performedAt;

    // ---- Extra info ----
    private String remarks;
}
