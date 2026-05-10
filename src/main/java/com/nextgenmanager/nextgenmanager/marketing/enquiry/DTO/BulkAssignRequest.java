package com.nextgenmanager.nextgenmanager.marketing.enquiry.DTO;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BulkAssignRequest {
    private List<Long> ids;
    private Long assignedToId;
}
