package com.nextgenmanager.nextgenmanager.production.service;


import com.nextgenmanager.nextgenmanager.production.model.WorkOrderProductionTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public interface WorkOrderProductionTemplateService {

    public WorkOrderProductionTemplate getWorkOrderProductionTemplate(int id);

    public WorkOrderProductionTemplate getWorkOrderProductionTemplateByBomId(int id);

    public List<WorkOrderProductionTemplate> getWorkOrderProductionTemplateList();

    @Transactional(propagation = Propagation.REQUIRED)
    public WorkOrderProductionTemplate createWorkOrderProductionTemplate(WorkOrderProductionTemplate workOrderProductionTemplate);

    @Transactional(propagation = Propagation.REQUIRED)
    public WorkOrderProductionTemplate updateWorkOrderProductionTemplate(int id,WorkOrderProductionTemplate workOrderProductionTemplate);

    public void deleteWorkOrderProductionTemplate(int id);

}
