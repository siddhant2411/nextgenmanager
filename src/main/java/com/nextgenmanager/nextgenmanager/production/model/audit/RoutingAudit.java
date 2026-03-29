package com.nextgenmanager.nextgenmanager.production.model.audit;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.util.Date;

@Entity
@Getter
@Setter
@Table(name = "routingAudit")
public class RoutingAudit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String action;       // Example: ROUTING_STATUS_CHANGE
    private String actor;        // Username or ID
    private String details;      // Free text payload/JSON string

    @CreationTimestamp
    private Date timestamp;
}
