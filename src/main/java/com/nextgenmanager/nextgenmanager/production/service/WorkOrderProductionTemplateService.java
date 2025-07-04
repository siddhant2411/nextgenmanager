package com.nextgenmanager.nextgenmanager.production.service;


import com.nextgenmanager.nextgenmanager.production.model.WorkOrderProductionTemplate;

import java.util.List;

public interface WorkOrderProductionTemplateService {

    public WorkOrderProductionTemplate getWorkOrderProductionTemplate(int id);

    public WorkOrderProductionTemplate getWorkOrderProductionTemplateByBomId(int id);

    public List<WorkOrderProductionTemplate> getWorkOrderProductionTemplateList();

    public WorkOrderProductionTemplate createWorkOrderProductionTemplate(WorkOrderProductionTemplate workOrderProductionTemplate);

    public WorkOrderProductionTemplate updateWorkOrderProductionTemplate(int id,WorkOrderProductionTemplate workOrderProductionTemplate);

    public void deleteWorkOrderProductionTemplate(int id);

}
