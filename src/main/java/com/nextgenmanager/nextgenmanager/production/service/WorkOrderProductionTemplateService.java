package com.nextgenmanager.nextgenmanager.production.service;


import com.nextgenmanager.nextgenmanager.production.dto.WorkOrderProductionTemplateResponseDTO;
import com.nextgenmanager.nextgenmanager.production.model.WorkOrderJobList;
import com.nextgenmanager.nextgenmanager.production.model.WorkOrderProductionTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

@Service
public interface WorkOrderProductionTemplateService {

    public WorkOrderProductionTemplateResponseDTO getWorkOrderProductionTemplate(int id);

    public WorkOrderProductionTemplate getWorkOrderProductionTemplateEntity(int id);

    public WorkOrderProductionTemplate getWorkOrderProductionTemplateByBomId(int bomId);

    public List<WorkOrderProductionTemplateResponseDTO> getWorkOrderProductionTemplateList();

    @Transactional(propagation = Propagation.REQUIRED)
    public WorkOrderProductionTemplateResponseDTO createWorkOrderProductionTemplate(WorkOrderProductionTemplate workOrderProductionTemplate);

    @Transactional(propagation = Propagation.REQUIRED)
    public WorkOrderProductionTemplateResponseDTO updateWorkOrderProductionTemplate(int id,WorkOrderProductionTemplate workOrderProductionTemplate);

    public void deleteWorkOrderProductionTemplate(int id);

    WorkOrderProductionTemplate getActiveVersion(int bomId);

    WorkOrderProductionTemplate createNewVersion(int bomId, WorkOrderProductionTemplate wopt);


    void activateVersion(int versionId);



    // --- JOB LIST MANAGEMENT ---

    @Transactional
    WorkOrderJobList addJobToTemplate(int woptId, WorkOrderJobList job);

    @Transactional
    WorkOrderJobList updateJobInTemplate(int woptId, int jobId, WorkOrderJobList job);

    @Transactional
    void removeJobFromTemplate(int woptId, int jobId);

    List<WorkOrderJobList> getJobListForTemplate(int woptId);


    // --- COSTING + CALCULATION METHODS ---
    WorkOrderProductionTemplate recalculateTotals(int woptId);

    BigDecimal calculateSetupTime(int woptId);

    BigDecimal calculateRunTime(int woptId);

    BigDecimal calculateLabourCost(int woptId);

    BigDecimal calculateBomCost(int woptId);

    BigDecimal calculateOverheadCost(int woptId);


    // --- VALIDATION ---
    boolean validateOperationSequence(int woptId);

    boolean validateEffectiveDates(Date effectiveFrom, Date effectiveTo);

    boolean validateConsistencyWithBOM(int bomId, int woptId);




}
