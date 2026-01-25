package com.nextgenmanager.nextgenmanager.production.service;

import com.nextgenmanager.nextgenmanager.bom.service.ResourceNotFoundException;
import com.nextgenmanager.nextgenmanager.production.dto.WorkCenterResponseDTO;
import com.nextgenmanager.nextgenmanager.production.mapper.WorkCenterResponseMapper;
import com.nextgenmanager.nextgenmanager.production.model.WorkCenter;
import com.nextgenmanager.nextgenmanager.production.repository.WorkCenterRepository;
import jakarta.persistence.criteria.Predicate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;

@Service
public class WorkCenterServiceImpl implements WorkCenterService {

    @Autowired
    private WorkCenterRepository workCenterRepository;

    @Autowired
    private WorkCenterResponseMapper workCenterResponseMapper;

    @Override
    public WorkCenterResponseDTO createWorkCenter(WorkCenter workCenter) {
        if (workCenterRepository.existsByCenterCode(workCenter.getCenterCode())) {
            throw new ResourceNotFoundException("Work Center code already exists.");
        }
        return workCenterResponseMapper.toDTO(workCenterRepository.save(workCenter));

    }

    @Override
    public WorkCenterResponseDTO updateWorkCenter(int id, WorkCenter updatedCenter) {
        if (workCenterRepository.findById(id).orElseThrow(() ->
                new ResourceNotFoundException("Work Center does not exist")
        ).getDeletedDate() != null) {
            throw new ResourceNotFoundException("Work Center does not exist");
        }
        return workCenterResponseMapper.toDTO(workCenterRepository.save(updatedCenter));
    }

    @Override
    public void deleteWorkCenter(int id) {

        WorkCenter workCenter = workCenterRepository.findById(id).orElseThrow(() ->
                new ResourceNotFoundException("Work Center does not exist"));

        if(workCenter.getDeletedDate()!=null){
            throw new ResourceNotFoundException("Work Center does not exist");
        }

        workCenter.setDeletedDate(new Date());

    }

    @Override
    public WorkCenterResponseDTO getWorkCenterById(int id) {
        WorkCenter workCenter = workCenterRepository.findById(id).orElseThrow(() ->
                new ResourceNotFoundException("Work Center does not exist"));

        if(workCenter.getDeletedDate()!=null){
            throw new ResourceNotFoundException("Work Center does not exist");
        }
        return workCenterResponseMapper.toDTO(workCenter);

    }

    @Override
    public WorkCenter getWorkCenterEntityById(int id) {
        WorkCenter workCenter = workCenterRepository.findById(id).orElseThrow(() ->
                new ResourceNotFoundException("Work Center does not exist"));

        if(workCenter.getDeletedDate()!=null){
            throw new ResourceNotFoundException("Work Center does not exist");
        }
        return workCenter;

    }


    @Override
    public WorkCenterResponseDTO getWorkCenterByCode(String centerCode) {
        return null;
    }

    @Override
    public List<WorkCenter> getAllWorkCenters() {
        return List.of();
    }

    @Override
    public List<WorkCenter> getAllActiveWorkCenters() {
        return List.of();
    }

    @Override
    public List<WorkCenter> searchByNameOrCode(String query) {
        return List.of();
    }

    @Override
    public boolean existsByCenterCode(String centerCode) {
        return false;
    }

    @Override
    public WorkCenter updateStatus(int id, WorkCenter.WorkCenterStatus status) {
        return null;
    }

    @Override
    public List<WorkCenter> getCentersByStatus(WorkCenter.WorkCenterStatus status) {
        return List.of();
    }

    @Override
    public Page<WorkCenterResponseDTO> getPaginatedCenters(int page, int size, String sortBy, String sortDir, String search) {
        Pageable pageable = PageRequest.of(page, size,
                sortDir.equalsIgnoreCase("desc") ? Sort.by(sortBy).descending() : Sort.by(sortBy).ascending());

        Specification<WorkCenter> spec = (root, query, cb) -> {
            Predicate predicate = cb.isNull(root.get("deletedDate"));
            if (search != null && !search.isEmpty()) {
                Predicate centerName = cb.like(cb.lower(root.get("centerName")), "%" + search.toLowerCase() + "%");
                Predicate centerCode = cb.like(cb.lower(root.get("centerCode")), "%" + search.toLowerCase() + "%");
                predicate = cb.and(predicate, centerName);
                predicate = cb.and(predicate, centerCode);
            }
            return predicate;
        };

        Page<WorkCenter> workCenters = workCenterRepository.findAll(spec, pageable);
        Page<WorkCenterResponseDTO> workCenterResponseDTOS = workCenters.map(workCenter -> workCenterResponseMapper.toDTO(workCenter));
        return workCenterResponseDTOS;
    }
}
