package com.nextgenmanager.nextgenmanager.production.model;

import com.nextgenmanager.nextgenmanager.production.model.workCenter.WorkCenter;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.util.Date;

@Entity
@Table(name = "scheduleDecisionLog",
        indexes = {
                @Index(name = "idx_sdl_wo", columnList = "workOrderId"),
                @Index(name = "idx_sdl_wc_date", columnList = "workCenterId, scheduledDate")
        })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ScheduleDecisionLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "workOrderId")
    private WorkOrder workOrder;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "workOrderOperationId")
    private WorkOrderOperation workOrderOperation;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "workCenterId")
    private WorkCenter workCenter;

    private Date scheduledDate;

    private int availableMinutes;

    private int consumedMinutes;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "machineId")
    private com.nextgenmanager.nextgenmanager.assets.model.MachineDetails machine;

    @Column(length = 1000)
    private String reason;

    @CreationTimestamp
    @Column(updatable = false)
    private Date createdAt;
}
