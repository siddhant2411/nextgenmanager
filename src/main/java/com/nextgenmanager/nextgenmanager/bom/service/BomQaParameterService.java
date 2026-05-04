package com.nextgenmanager.nextgenmanager.bom.service;

import com.nextgenmanager.nextgenmanager.bom.dto.BomQaParameterDTO;
import com.nextgenmanager.nextgenmanager.bom.dto.BomQaParameterRequestDTO;

import java.util.List;

public interface BomQaParameterService {

    List<BomQaParameterDTO> getParametersForOperation(Long routingOperationId);

    BomQaParameterDTO addParameter(Long routingOperationId, BomQaParameterRequestDTO request);

    BomQaParameterDTO updateParameter(Long parameterId, BomQaParameterRequestDTO request);

    void deleteParameter(Long parameterId);
}
