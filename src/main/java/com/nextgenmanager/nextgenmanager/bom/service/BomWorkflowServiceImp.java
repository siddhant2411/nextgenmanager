package com.nextgenmanager.nextgenmanager.bom.service;

import com.nextgenmanager.nextgenmanager.bom.dto.BOMRoutingMapper;
import com.nextgenmanager.nextgenmanager.bom.dto.BOMRoutingRequestMapper;
import com.nextgenmanager.nextgenmanager.bom.mapper.BomMapper;
import com.nextgenmanager.nextgenmanager.bom.model.Bom;
import com.nextgenmanager.nextgenmanager.production.dto.RoutingDto;
import com.nextgenmanager.nextgenmanager.production.mapper.RoutingMapper;
import com.nextgenmanager.nextgenmanager.production.model.Routing;
import com.nextgenmanager.nextgenmanager.production.model.RoutingOperation;
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

    @Autowired
    private com.nextgenmanager.nextgenmanager.bom.repository.BomPositionRepository bomPositionRepository;

    private void linkOperationsToBomPositions(Bom bom, BOMRoutingRequestMapper requestMapper, RoutingDto savedRouting) {
        if (bom == null || bom.getPositions() == null || bom.getPositions().isEmpty() ||
            savedRouting == null || savedRouting.getOperations() == null ||
            requestMapper == null || requestMapper.getBom() == null || requestMapper.getBom().getPositions() == null) {
            return;
        }

        java.util.Map<Integer, com.nextgenmanager.nextgenmanager.production.dto.RoutingOperationDto> opBySeq =
                savedRouting.getOperations().stream()
                        .filter(op -> op.getSequenceNumber() != null)
                        .collect(java.util.stream.Collectors.toMap(
                                com.nextgenmanager.nextgenmanager.production.dto.RoutingOperationDto::getSequenceNumber,
                                op -> op, (a, b) -> a));

        boolean anyUpdated = false;
        for (com.nextgenmanager.nextgenmanager.bom.model.BomPosition pos : bom.getPositions()) {
            // Find corresponding request position
            for (com.nextgenmanager.nextgenmanager.bom.dto.BomPositionRequestDTO reqPos : requestMapper.getBom().getPositions()) {
                if (reqPos.getChildInventoryItem() != null && pos.getChildInventoryItem() != null &&
                    reqPos.getChildInventoryItem().getInventoryItemId() == pos.getChildInventoryItem().getInventoryItemId() &&
                    (reqPos.getPosition() == null || reqPos.getPosition().equals(pos.getPosition()))) {
                    
                    if (reqPos.getRoutingOperationSequenceNumber() != null) {
                        com.nextgenmanager.nextgenmanager.production.dto.RoutingOperationDto matchingOp =
                                opBySeq.get(reqPos.getRoutingOperationSequenceNumber());
                        if (matchingOp != null && matchingOp.getId() != null) {
                            RoutingOperation opEntity = new RoutingOperation();
                            opEntity.setId(matchingOp.getId());
                            pos.setRoutingOperation(opEntity);
                            anyUpdated = true;
                        }
                    }
                }
            }
        }

        if (anyUpdated) {
            bomPositionRepository.saveAll(bom.getPositions());
        }
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    public BOMRoutingMapper createBomWithRouting(BOMRoutingRequestMapper bomRoutingRequestMapper) {

        logger.debug("Received request to add new BOM");
        try {

            Bom savedBom = bomService.addBom(bomRoutingRequestMapper.toBomEntity());
            logger.debug("BOM saved with ID: {}", savedBom.getId());

            // Create associated WorkOrderProductionTemplate
            Routing routing = bomRoutingRequestMapper.getRouting();
            routing.setBom(savedBom); // Ensure linkage
            RoutingDto savedRouting =
                    routingService.createOrUpdateRouting(savedBom.getId(),routingMapper.toDTO(routing),"SYSTEM");

            logger.debug("WorkOrderProductionTemplate saved with ID: {}", savedRouting.getId());

            linkOperationsToBomPositions(savedBom, bomRoutingRequestMapper, savedRouting);

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
            // Because BomServiceImpl.editBom complains if a pos points to an unsaved temp ID,
            // we should null out the routingOperation for those positions before calling editBom
            Bom bomToUpdate = bomTemplateMapper.toBomEntity();
            if (bomToUpdate.getPositions() != null) {
                for (com.nextgenmanager.nextgenmanager.bom.model.BomPosition pos : bomToUpdate.getPositions()) {
                    if (pos.getRoutingOperation() != null && pos.getRoutingOperation().getId() != null) {
                        // Check if it's a large temp ID (timestamp)
                        if (pos.getRoutingOperation().getId() > 1000000000000L) {
                            pos.setRoutingOperation(null);
                        }
                    }
                }
            }

            bomToUpdate.setId(bomId);
            Bom updatedBom = bomService.editBom(bomId,bomToUpdate);

            Routing routing = bomTemplateMapper.getRouting();
            routing.setBom(updatedBom);
            RoutingDto routingDto = routingService.createOrUpdateRouting(bomId,routingMapper.toDTO(routing),"SYSTEM");
            
            linkOperationsToBomPositions(updatedBom, bomTemplateMapper, routingDto);

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

