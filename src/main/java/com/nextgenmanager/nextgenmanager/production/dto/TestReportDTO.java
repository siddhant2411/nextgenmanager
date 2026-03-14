package com.nextgenmanager.nextgenmanager.production.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;
import java.util.List;

/**
 * Summary report of all QC tests for a Work Order.
 * Used for the test-report endpoint.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TestReportDTO {

    private int workOrderId;
    private String workOrderNumber;
    private String productName;
    private String productCode;

    private int totalTests;
    private int completedTests;
    private int passedTests;
    private int failedTests;
    private int pendingTests;

    /** true if all mandatory tests passed */
    private boolean overallPass;

    private Date reportDate;

    private List<WorkOrderTestResultDTO> testResults;
}
