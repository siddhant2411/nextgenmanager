package com.nextgenmanager.nextgenmanager.production.service;

import com.nextgenmanager.nextgenmanager.production.dto.CostOfProductionDTO;

public interface CostOfProductionService {
    CostOfProductionDTO compute(int workOrderId);
}
