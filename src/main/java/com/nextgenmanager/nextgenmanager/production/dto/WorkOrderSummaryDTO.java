package com.nextgenmanager.nextgenmanager.production.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class WorkOrderSummaryDTO {

    private long overdue;
    private long dueSoon;
    private long ready;
    private long inProgress;
    private long completingToday;
    private long blocked;
}
