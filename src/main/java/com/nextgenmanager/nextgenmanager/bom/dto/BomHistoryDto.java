package com.nextgenmanager.nextgenmanager.bom.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BomHistoryDto {
    private Integer id;
    private String bomName;
    private String bomCode;
    private String revision;
    private String bomStatus;
    private Date effectiveFrom;
    private Date effectiveTo;
    private Date creationDate;
    private Date updatedDate;
}

