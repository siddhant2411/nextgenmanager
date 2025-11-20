package com.nextgenmanager.nextgenmanager.bom.service;

import com.nextgenmanager.nextgenmanager.bom.controller.BomController;
import com.nextgenmanager.nextgenmanager.bom.dto.BOMTemplateMapper;
import com.nextgenmanager.nextgenmanager.bom.model.Bom;
import com.nextgenmanager.nextgenmanager.production.model.WorkOrderProductionTemplate;
import com.nextgenmanager.nextgenmanager.production.service.WorkOrderProductionTemplateService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;


@Service
public class BomWorkflowServiceImp implements BomWorkflowService {

    private static final Logger logger = LoggerFactory.getLogger(BomWorkflowService.class);

    @Autowired
    private BomService bomService;

    @Autowired
    private WorkOrderProductionTemplateService workOrderProductionTemplateService;

    @Override
    @Transactional(propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    public BOMTemplateMapper createBomWithTemplate(BOMTemplateMapper bomTemplateMapper) {

        logger.debug("Received request to add new BOM");
        try {

            Bom savedBom = bomService.addBom(bomTemplateMapper.getBom());
            logger.debug("BOM saved with ID: {}", savedBom.getId());

            // Create associated WorkOrderProductionTemplate
            WorkOrderProductionTemplate inputTemplate = bomTemplateMapper.getWorkOrderProductionTemplate();
            inputTemplate.setBom(savedBom); // Ensure linkage
            WorkOrderProductionTemplate savedTemplate =
                    workOrderProductionTemplateService.createWorkOrderProductionTemplate(inputTemplate);

            logger.debug("WorkOrderProductionTemplate saved with ID: {}", savedTemplate.getId());

            // Prepare response
            BOMTemplateMapper newBomTemplateMapper = new BOMTemplateMapper();
            newBomTemplateMapper.setBom(savedBom);
//            newBomTemplateMapper.setWorkOrderProductionTemplate(savedTemplate);

            logger.info("Successfully created BOM and WorkOrderProductionTemplate with BOM ID: {}", savedBom.getId());
            return newBomTemplateMapper;

        } catch (Exception e) {
            logger.error("Failed to create BOM: {}", e.getMessage(), e);
            throw new RuntimeException(e.getMessage());
        }

    }


    @Override
    @Transactional(propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    public BOMTemplateMapper updateBomWithTemplate(int bomId,BOMTemplateMapper bomTemplateMapper) {
        logger.debug("Received request to update BOM with id: {}", bomId);
        try {
            Bom bomToUpdate = bomTemplateMapper.getBom();
            bomToUpdate.setId(bomId);
            Bom updatedBom = bomService.editBom(bomToUpdate);

            WorkOrderProductionTemplate template = bomTemplateMapper.getWorkOrderProductionTemplate();
            template.setBom(updatedBom);
            BOMTemplateMapper responseMapper = new BOMTemplateMapper();
            responseMapper.setBom(updatedBom);
            if(template.getId()>0) {
                WorkOrderProductionTemplate updatedTemplate = workOrderProductionTemplateService.updateWorkOrderProductionTemplate(template.getId(), template);
                responseMapper.setWorkOrderProductionTemplate(updatedTemplate);
            }else {
                WorkOrderProductionTemplate workOrderProductionTemplate = workOrderProductionTemplateService.createWorkOrderProductionTemplate(template);
                responseMapper.setWorkOrderProductionTemplate(workOrderProductionTemplate);
            }


            logger.info("Successfully updated BOM and Template for ID: {}", bomId);
            return responseMapper;

        } catch (Exception e) {
            logger.error("Failed to update BOM with ID {}: {}", bomId, e.getMessage(), e);
            throw new RuntimeException(e.getMessage());
        }

    }
}
