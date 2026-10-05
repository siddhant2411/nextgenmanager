package com.nextgenmanager.nextgenmanager.common.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Date;

/**
 * One user's acceptance of one version of the user agreement. Rows are never updated or deleted:
 * together they are the record of who agreed to which text, when, and from where.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "useragreementacceptance")
public class UserAgreementAcceptance {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "userId", referencedColumnName = "id", nullable = false)
    private AppUser appUser;

    @Column(nullable = false, length = 50)
    private String agreementVersion;

    @Column(nullable = false)
    private Date acceptedDate;

    @Column(length = 64)
    private String ipAddress;

    @Column(length = 500)
    private String userAgent;
}
