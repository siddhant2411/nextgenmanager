package com.nextgenmanager.nextgenmanager.bom.service;

import com.nextgenmanager.nextgenmanager.bom.dto.BOMTemplateMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public interface BomWorkflowService {

    @Transactional(propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    public BOMTemplateMapper createBomWithTemplate(BOMTemplateMapper bomTemplateMapper);

    @Transactional(propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    public BOMTemplateMapper updateBomWithTemplate(int bomId,BOMTemplateMapper bomTemplateMapper);
}
