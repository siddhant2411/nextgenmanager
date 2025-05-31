package com.nextgenmanager.nextgenmanager.production.service;

import com.nextgenmanager.nextgenmanager.production.DTO.WorkOrderProductionDTO;
import com.nextgenmanager.nextgenmanager.production.model.WorkOrderProduction;
import com.nextgenmanager.nextgenmanager.production.model.WorkOrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.repository.JpaRepository;

import java.math.BigDecimal;
import java.util.Date;

public interface WorkOrderProductionService {

    public WorkOrderProduction getWorkOrderProductionJobById(int id);

    public Page<WorkOrderProductionDTO> getWorkOrderProductionList(WorkOrderProductionDTO workOrderProductionDTOFilter);

    public WorkOrderProductionDTO createWorkOrderProduction(WorkOrderProduction newWorkOrderProduction);
}
