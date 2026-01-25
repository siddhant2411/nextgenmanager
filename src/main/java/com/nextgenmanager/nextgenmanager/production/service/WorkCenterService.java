package com.nextgenmanager.nextgenmanager.production.service;

import com.nextgenmanager.nextgenmanager.production.dto.WorkCenterResponseDTO;
import com.nextgenmanager.nextgenmanager.production.model.WorkCenter;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface WorkCenterService {

    // Create
    WorkCenterResponseDTO createWorkCenter(WorkCenter workCenter);

    // Update
    WorkCenterResponseDTO updateWorkCenter(int id, WorkCenter updatedCenter);

    // Soft delete
    void deleteWorkCenter(int id);

    // Get single
    WorkCenterResponseDTO getWorkCenterById(int id);

    WorkCenter getWorkCenterEntityById(int id);

    WorkCenterResponseDTO getWorkCenterByCode(String centerCode);

    // List all
    List<WorkCenter> getAllWorkCenters();
    List<WorkCenter> getAllActiveWorkCenters();

    // Search & filter
    List<WorkCenter> searchByNameOrCode(String query);
    boolean existsByCenterCode(String centerCode);

    // Status-related
    WorkCenter updateStatus(int id, WorkCenter.WorkCenterStatus status);
    List<WorkCenter> getCentersByStatus(WorkCenter.WorkCenterStatus status);

    // Pagination with filter
    Page<WorkCenterResponseDTO> getPaginatedCenters(int page, int size, String sortBy, String sortDir, String search);
}
