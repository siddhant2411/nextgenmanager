package com.nextgenmanager.nextgenmanager.common.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.FetchType;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.util.Date;
import java.util.List;
import java.util.Locale;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "role")
public class Role {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id;

    @Column(nullable = false, unique = true, length = 100)
    private String roleName;

    @Column(nullable = false, length = 120)
    private String displayName;

    @Column(length = 500)
    private String roleDescription;

    @Column(length = 100)
    private String moduleName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private RoleType roleType = RoleType.SYSTEM;

    @Column(nullable = false)
    private Boolean isSystemRole = false;

    @Column(nullable = false)
    private Boolean isActive = true;

    @Column(length = 100)
    private String createdBy;

    @Column(length = 100)
    private String updatedBy;

    @OneToMany(mappedBy = "role", fetch = FetchType.LAZY)
    private List<UserRoleMap> userRoleMaps;

    @CreationTimestamp
    @Column(updatable = false)
    private Date creationDate;

    @UpdateTimestamp
    private Date updatedDate;

    private Date deletedDate;

    @PrePersist
    @PreUpdate
    private void normalizeAndDefault() {
        if (roleName != null) {
            roleName = roleName.trim().toUpperCase(Locale.ROOT);
        }
        if (displayName != null) {
            displayName = displayName.trim();
        }
        if (moduleName != null && moduleName.isBlank()) {
            moduleName = null;
        }
        if (roleType == null) {
            roleType = RoleType.SYSTEM;
        }
        if (isSystemRole == null) {
            isSystemRole = false;
        }
        if (isActive == null) {
            isActive = true;
        }
    }
}
