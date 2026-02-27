package com.nextgenmanager.nextgenmanager.assets.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "machineStatusHistory",
        indexes = {
                @Index(name = "idx_msh_machine_changed_at", columnList = "machineId, changedAt")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class MachineStatusHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "machineId", nullable = false)
    private MachineDetails machine;

    @Enumerated(EnumType.STRING)
    @Column(name = "oldStatus", length = 30)
    private MachineDetails.MachineStatus oldStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "newStatus", nullable = false, length = 30)
    private MachineDetails.MachineStatus newStatus;

    @Column(name = "changedAt", nullable = false)
    private LocalDateTime changedAt  = LocalDateTime.now(); ;

    @Column(name = "changedBy", length = 100)
    private String changedBy;

    @Column(length = 500)
    private String reason;

    @Column(nullable = false)
    @CreationTimestamp
    private LocalDateTime createdAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Source source;

    public enum Source {
        MANUAL,
        SYSTEM
    }
}
