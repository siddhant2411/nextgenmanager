package com.nextgenmanager.nextgenmanager.production.model.workCenter;

import com.nextgenmanager.nextgenmanager.assets.model.MachineDetails;
import com.nextgenmanager.nextgenmanager.production.model.WorkOrderOperation;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.util.Date;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "downtimeEvent", indexes = {
    @Index(name = "idx_dt_machine", columnList = "machineId"),
    @Index(name = "idx_dt_active", columnList = "machineId, endTime")
})
public class DowntimeEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "machineId", nullable = false)
    private MachineDetails machine;

    @Column(name = "shiftId")
    private Long shiftId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reasonCodeId", nullable = false)
    private DowntimeReasonCode reasonCode;

    @Column(nullable = false)
    private Date startTime;

    private Date endTime;

    private Integer durationMinutes;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "workOrderOperationId")
    private WorkOrderOperation workOrderOperation;

    @Column(length = 500)
    private String remarks;

    @Column(length = 100)
    private String reportedBy;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Date createdAt;
}
