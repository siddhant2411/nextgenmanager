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
        name = "machineEvent",
        indexes = {
                @Index(name = "idx_me_machine_start_time", columnList = "machineId, startTime")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class MachineEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "machineId", nullable = false)
    private MachineDetails machine;

    @Enumerated(EnumType.STRING)
    @Column(name = "eventType", nullable = false, length = 30)
    private EventType eventType;

    @Column(name = "startTime", nullable = false)
    private LocalDateTime startTime;

    @Column(name = "endTime")
    private LocalDateTime endTime;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Source source;

    @Column(nullable = false)
    @CreationTimestamp
    private LocalDateTime createdAt;

    public enum EventType {
        RUNNING,
        IDLE,
        BREAKDOWN,
        MAINTENANCE
    }

    public enum Source {
        MANUAL,
        SYSTEM
    }
}
