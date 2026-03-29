package com.nextgenmanager.nextgenmanager.production.dto;

import lombok.*;

import java.math.BigDecimal;


@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductionJobResponseDTO {
    private int id;
    private String jobCode;
    private String jobName;
    private BigDecimal defaultSetupTime;
    private BigDecimal defaultRunTimePerUnit;
    private String description;
    private boolean active;
}
