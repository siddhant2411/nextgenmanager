package com.nextgenmanager.nextgenmanager.marketing.enquiry.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.util.Date;

/**
 * Master list of reasons an enquiry can be closed with.
 *
 * Kept as a table rather than an enum so sales can add a reason without a deployment,
 * and deactivate one without orphaning the enquiries that already reference it.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "enquiryCloseReason")
public class EnquiryCloseReason {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false, length = 50)
    private String code;

    @Column(length = 255)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EnquiryCloseOutcome outcome = EnquiryCloseOutcome.LOST;

    /** Controls dropdown order; lower sorts first. */
    @Column(nullable = false)
    private Integer displayOrder = 100;

    @Column(nullable = false)
    private Boolean isActive = true;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Date creationDate;
}
