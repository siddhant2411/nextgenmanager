package com.nextgenmanager.nextgenmanager.bom.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChangeLogDto {
    private Integer id;
    private Integer bomId;
    private String action;        // action | changeType | event
    private String description;   // description | changeReason | comments
    private String changedBy;     // changedBy | updatedBy | user | userName
    private Date changedAt;       // changedAt | updatedAt | timestamp | createdAt

    // optional detailed fields for field-level changes
    private String fieldName;
    private String oldValue;
    private String newValue;

    // optional alternate fields
    private String comments;
    private String userName;
}

