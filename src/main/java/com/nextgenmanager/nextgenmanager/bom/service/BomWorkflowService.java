package com.nextgenmanager.nextgenmanager.bom.service;

import com.nextgenmanager.nextgenmanager.bom.dto.BOMRoutingMapper;
import com.nextgenmanager.nextgenmanager.bom.dto.BOMRoutingRequestMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public interface BomWorkflowService {

    @Transactional(propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    public BOMRoutingMapper createBomWithRouting(BOMRoutingRequestMapper bomRoutingRequestMapper);

    @Transactional(propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    public BOMRoutingMapper updateBomWithTemplate(int bomId,BOMRoutingRequestMapper bomTemplateMapper);
}
