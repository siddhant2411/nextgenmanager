package com.nextgenmanager.nextgenmanager.production.service;

import com.nextgenmanager.nextgenmanager.production.dto.JobWorkChallanDTO;
import com.nextgenmanager.nextgenmanager.production.dto.JobWorkChallanReceiptDTO;
import com.nextgenmanager.nextgenmanager.production.dto.JobWorkChallanRequestDTO;
import com.nextgenmanager.nextgenmanager.production.enums.ChallanStatus;

import java.util.List;

public interface JobWorkChallanService {

    /** Create a new challan in DRAFT status. */
    JobWorkChallanDTO create(JobWorkChallanRequestDTO request);

    /** Update a DRAFT challan (lines, vendor, remarks). */
    JobWorkChallanDTO update(Long id, JobWorkChallanRequestDTO request);

    /** Dispatch the challan: DRAFT → DISPATCHED. Sets dispatchDate and expectedReturnDate (+180 days). */
    JobWorkChallanDTO dispatch(Long id);

    /**
     * Record a receipt of materials from the job worker.
     * Updates line quantities; transitions to PARTIALLY_RECEIVED or COMPLETED automatically.
     */
    JobWorkChallanDTO receiveBack(Long id, JobWorkChallanReceiptDTO receipt);

    /** Cancel a DRAFT or DISPATCHED challan. */
    JobWorkChallanDTO cancel(Long id);

    JobWorkChallanDTO getById(Long id);

    List<JobWorkChallanDTO> getAll();

    List<JobWorkChallanDTO> getByVendor(int vendorId);

    List<JobWorkChallanDTO> getByWorkOrder(Long workOrderId);

    List<JobWorkChallanDTO> getByStatus(ChallanStatus status);

    /** All DISPATCHED/PARTIALLY_RECEIVED challans past the 180-day return deadline. */
    List<JobWorkChallanDTO> getOverdue();

    /** Soft-delete a DRAFT challan. */
    void delete(Long id);
}
