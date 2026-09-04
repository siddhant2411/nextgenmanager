package com.nextgenmanager.nextgenmanager.quality.service;

import com.nextgenmanager.nextgenmanager.quality.dto.InspectionJudgeRequest;
import com.nextgenmanager.nextgenmanager.quality.dto.InspectionLotCreateRequest;
import com.nextgenmanager.nextgenmanager.quality.dto.InspectionLotDto;
import com.nextgenmanager.nextgenmanager.quality.dto.InspectionWaiverRequest;
import com.nextgenmanager.nextgenmanager.quality.model.InspectionLotStatus;
import com.nextgenmanager.nextgenmanager.quality.model.InspectionSource;

import java.util.List;

/** Raising inspections and recording what they found. The gate is what reads the answer. */
public interface InspectionLotService {

    List<InspectionLotDto> list(InspectionSource source, InspectionLotStatus status);

    InspectionLotDto get(Long id);

    /** Every lot raised against a work order — what the production screens show beside it. */
    List<InspectionLotDto> forWorkOrder(int workOrderId);

    /** Raises a lot in PENDING. Nothing is judged and nothing is blocked until it is. */
    InspectionLotDto raise(InspectionLotCreateRequest request);

    /**
     * Records the verdict. A lot fails when any critical check failed, or when nothing was
     * accepted; otherwise it passes. The inspector's checks decide it — the status is not asked
     * for directly, because a sheet full of failures and a status of PASSED is a contradiction
     * nobody should be able to save.
     */
    InspectionLotDto judge(Long id, InspectionJudgeRequest request);

    /**
     * Releases goods that failed, on somebody's authority. Recorded as WAIVED rather than turned
     * into a pass, so what actually happened stays legible afterwards.
     */
    InspectionLotDto waive(Long id, InspectionWaiverRequest request);

    /** Abandons a lot raised in error. Only while it is still PENDING — a verdict is a record. */
    void cancel(Long id);
}
