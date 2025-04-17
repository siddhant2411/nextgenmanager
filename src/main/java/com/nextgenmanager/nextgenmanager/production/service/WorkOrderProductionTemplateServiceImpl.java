package com.nextgenmanager.nextgenmanager.production.service;



import com.nextgenmanager.nextgenmanager.production.model.WorkOrderProductionTemplate;
import com.nextgenmanager.nextgenmanager.production.repository.WorkOrderProductionTemplateRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class WorkOrderProductionTemplateServiceImpl implements WorkOrderProductionTemplateService{

    private static final Logger logger = LoggerFactory.getLogger(WorkOrderProductionTemplateServiceImpl.class);

    @Autowired
    private WorkOrderProductionTemplateRepository workOrderProductionTemplateRepository;

    public WorkOrderProductionTemplate getWorkOrderProductionTemplate(int id){

        logger.debug("Fetching workOrderProductionTemplate for ID: {}", id);
        return workOrderProductionTemplateRepository.findById(id)
                .filter(WOPTemplate -> WOPTemplate.getDeletedDate()==null)
                .orElseThrow(() -> {
                    logger.error("workOrderProductionTemplate not found for ID: {}", id);
                    return new RuntimeException("workOrderProductionTemplate not found for ID: " + id);
                });
    };

    public List<WorkOrderProductionTemplate> getWorkOrderProductionTemplateList(){

        logger.debug("Fetching all WOPTemplate");
        List<WorkOrderProductionTemplate> workOrderProductionTemplateList = workOrderProductionTemplateRepository.findAll().stream()
                .filter(WOPTemplate -> WOPTemplate.getDeletedDate() == null)
                .collect(Collectors.toList());
        logger.debug("Retrieved {} active workOrderProductionTemplate records", workOrderProductionTemplateList.size());
        return workOrderProductionTemplateList;
    };

    public WorkOrderProductionTemplate createWorkOrderProductionTemplate(WorkOrderProductionTemplate workOrderProductionTemplate){
        logger.debug("Creating new workOrderProductionTemplate: {}", workOrderProductionTemplate);
        WorkOrderProductionTemplate savedWorkOrderProductionTemplate = workOrderProductionTemplateRepository.save(workOrderProductionTemplate);
        logger.info("Successfully created workOrderProductionTemplate with ID: {}", savedWorkOrderProductionTemplate.getId());
        return savedWorkOrderProductionTemplate;
    };

    public WorkOrderProductionTemplate updateWorkOrderProductionTemplate(int id,WorkOrderProductionTemplate workOrderProductionTemplate){
        logger.info("Attempting to update workOrderProductionTemplate with ID: {}", id);

        WorkOrderProductionTemplate oldWorkOrderProductionTemplate = getWorkOrderProductionTemplate(id);
        WorkOrderProductionTemplate updatedWorkOrderProductionTemplate = workOrderProductionTemplateRepository.save(workOrderProductionTemplate);
        logger.info("workOrderProductionTemplate with ID: {} updated",updatedWorkOrderProductionTemplate.getId());
        return updatedWorkOrderProductionTemplate;


    }

    public void deleteWorkOrderProductionTemplate(int id){

        logger.debug("Attempting to soft workOrderProductionTemplate with ID: {}", id);
        WorkOrderProductionTemplate workOrderProductionTemplate = getWorkOrderProductionTemplate(id);
        workOrderProductionTemplate.setDeletedDate(new Date());
        workOrderProductionTemplateRepository.save(workOrderProductionTemplate);
        logger.info("Successfully soft deleted workOrderProductionTemplate with ID: {}", id);
    }
}
