package com.nextgenmanager.nextgenmanager.production.dto;

import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LaborRoleResponseDTO {
    private Long id;
    private String roleCode;
    private String roleName;
    private BigDecimal costPerHour;
    private String description;
    private boolean active;
}
