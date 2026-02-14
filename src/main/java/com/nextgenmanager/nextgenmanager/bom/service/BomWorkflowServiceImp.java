package com.nextgenmanager.nextgenmanager.bom.service;

import com.nextgenmanager.nextgenmanager.bom.dto.BOMRoutingMapper;
import com.nextgenmanager.nextgenmanager.bom.dto.BOMRoutingRequestMapper;
import com.nextgenmanager.nextgenmanager.bom.mapper.BomMapper;
import com.nextgenmanager.nextgenmanager.bom.model.Bom;
import com.nextgenmanager.nextgenmanager.production.dto.RoutingDto;
import com.nextgenmanager.nextgenmanager.production.mapper.RoutingMapper;
import com.nextgenmanager.nextgenmanager.production.model.Routing;
import com.nextgenmanager.nextgenmanager.production.service.RoutingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;


@Service
public class BomWorkflowServiceImp implements BomWorkflowService {

    private static final Logger logger = LoggerFactory.getLogger(BomWorkflowService.class);

    @Autowired
    private BomService bomService;

    @Autowired
    private RoutingService routingService;

    @Autowired
    private RoutingMapper routingMapper;

    @Override
    @Transactional(propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    public BOMRoutingMapper createBomWithRouting(BOMRoutingRequestMapper bomRoutingRequestMapper) {

        logger.debug("Received request to add new BOM");
        try {

            Bom savedBom = bomService.addBom(bomRoutingRequestMapper.getBom());
            logger.debug("BOM saved with ID: {}", savedBom.getId());

            // Create associated WorkOrderProductionTemplate
            Routing routing = bomRoutingRequestMapper.getRouting();
            routing.setBom(savedBom); // Ensure linkage
            RoutingDto savedRouting =
                    routingService.createOrUpdateRouting(savedBom.getId(),routingMapper.toDTO(routing),"SYSTEM");

            logger.debug("WorkOrderProductionTemplate saved with ID: {}", savedRouting.getId());

            // Prepare response
            BOMRoutingMapper newBomRoutingMapper = new BOMRoutingMapper();
            newBomRoutingMapper.setBom(BomMapper.toDto(savedBom));
            newBomRoutingMapper.setRouting(savedRouting);

            logger.info("Successfully created BOM and WorkOrderProductionTemplate with BOM ID: {}", savedBom.getId());
            return newBomRoutingMapper;

        } catch (Exception e) {
            logger.error("Failed to create BOM: {}", e.getMessage(), e);
            throw new RuntimeException(e.getMessage());
        }

    }


    @Override
    @Transactional(propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    public BOMRoutingMapper updateBomWithTemplate(int bomId,BOMRoutingRequestMapper bomTemplateMapper) {
        logger.debug("Received request to update BOM with id: {}", bomId);
        try {
            Bom bomToUpdate = bomTemplateMapper.getBom();
            bomToUpdate.setId(bomId);
            Bom updatedBom = bomService.editBom(bomId,bomToUpdate);

            Routing routing = bomTemplateMapper.getRouting();
            routing.setBom(updatedBom);
            RoutingDto routingDto = routingService.createOrUpdateRouting(bomId,routingMapper.toDTO(routing),"SYSTEM");
            BOMRoutingMapper responseMapper = new BOMRoutingMapper();
            responseMapper.setBom(BomMapper.toDto(updatedBom));
            responseMapper.setRouting(routingDto);


            logger.info("Successfully updated BOM and Template for ID: {}", bomId);
            return responseMapper;

        } catch (Exception e) {
            logger.error("Failed to update BOM with ID {}: {}", bomId, e.getMessage(), e);
            throw new RuntimeException(e.getMessage());
        }

    }
}
