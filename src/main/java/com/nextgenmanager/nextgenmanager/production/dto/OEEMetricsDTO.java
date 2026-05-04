package com.nextgenmanager.nextgenmanager.production.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OEEMetricsDTO {
    private Long machineId;
    private String machineName;
    private LocalDate date;
    
    private BigDecimal availability;
    private BigDecimal performance;
    private BigDecimal quality;
    private BigDecimal oee;
    
    private Integer runtimeMinutes;
    private Integer downtimeMinutes;
    private Integer actualQuantity;
    private Integer rejectedQuantity;
    private Integer plannedQuantity;
}
