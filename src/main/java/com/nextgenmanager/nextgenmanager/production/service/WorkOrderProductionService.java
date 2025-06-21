package com.nextgenmanager.nextgenmanager.production.service;

import com.nextgenmanager.nextgenmanager.production.DTO.WorkOrderProductionDTO;
import com.nextgenmanager.nextgenmanager.production.DTO.WorkOrderProductionRequestMapper;
import com.nextgenmanager.nextgenmanager.production.model.WorkOrderProduction;
import org.springframework.data.domain.Page;

import java.util.Optional;

public interface WorkOrderProductionService {

    public Optional<WorkOrderProduction> getWorkOrderProductionJobById(int id);

    public Page<WorkOrderProductionDTO> getWorkOrderProductionList(WorkOrderProductionDTO workOrderProductionDTOFilter,  int page, int size, String sortBy, String sorDir);

    public WorkOrderProductionDTO createWorkOrderProduction(WorkOrderProduction newWorkOrderProduction);

    public WorkOrderProduction mapWorkOrderProductionRequest(WorkOrderProductionRequestMapper workOrderProductionRequestMapper);
}
