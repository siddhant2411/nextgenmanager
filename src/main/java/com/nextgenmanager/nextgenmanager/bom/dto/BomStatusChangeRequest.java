package com.nextgenmanager.nextgenmanager.bom.dto;

import com.nextgenmanager.nextgenmanager.bom.model.BomStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BomStatusChangeRequest {

    private int id;
    private BomStatus nextStatus;
    private String ecoNumber;
    private String changeReason;
    private String approvalComments;

}
