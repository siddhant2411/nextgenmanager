package com.nextgenmanager.nextgenmanager.bom.dto;


import com.nextgenmanager.nextgenmanager.bom.model.Bom;
import com.nextgenmanager.nextgenmanager.production.model.WorkOrderProductionTemplate;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BOMTemplateMapper {

    private Bom bom;

    private WorkOrderProductionTemplate workOrderProductionTemplate;
}
