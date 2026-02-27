package com.nextgenmanager.nextgenmanager.production.model;

import com.nextgenmanager.nextgenmanager.assets.model.MachineDetails;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "ScheduleDecisionLog",
        indexes = {
            @Index(name="idx_sdl_machine_time", columnList="machineId, scheduledStart")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ScheduleDecisionLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "orderId", nullable = false)
    private Long orderId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "machineId", nullable = false)
    private MachineDetails machine;

    @Column(name = "scheduledStart")
    private LocalDateTime scheduledStart;

    @Column(name = "scheduledEnd")
    private LocalDateTime scheduledEnd;

    @Column(length = 1000)
    private String reason;
}
