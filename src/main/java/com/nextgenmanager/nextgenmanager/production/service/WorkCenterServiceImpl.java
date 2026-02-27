package com.nextgenmanager.nextgenmanager.production.service;

import com.nextgenmanager.nextgenmanager.bom.service.ResourceNotFoundException;
import com.nextgenmanager.nextgenmanager.production.dto.WorkCenterResponseDTO;
import com.nextgenmanager.nextgenmanager.production.mapper.WorkCenterResponseMapper;
import com.nextgenmanager.nextgenmanager.production.model.workCenter.WorkCenter;
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
import java.util.Objects;
import java.util.stream.Collectors;

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
        WorkCenter existingCenter = getActiveWorkCenterById(id);

        if (!Objects.equals(existingCenter.getCenterCode(), updatedCenter.getCenterCode())
                && existsByCenterCode(updatedCenter.getCenterCode())) {
            throw new ResourceNotFoundException("Work Center code already exists.");
        }

        existingCenter.setCenterCode(updatedCenter.getCenterCode());
        existingCenter.setCenterName(updatedCenter.getCenterName());
        existingCenter.setDescription(updatedCenter.getDescription());
        existingCenter.setCostPerHour(updatedCenter.getCostPerHour());
        existingCenter.setAvailableHoursPerDay(updatedCenter.getAvailableHoursPerDay());
        existingCenter.setWorkCenterStatus(updatedCenter.getWorkCenterStatus());
        existingCenter.setDepartment(updatedCenter.getDepartment());
        existingCenter.setLocation(updatedCenter.getLocation());
        existingCenter.setMaxLoadPercentage(updatedCenter.getMaxLoadPercentage());
        existingCenter.setSupervisor(updatedCenter.getSupervisor());
        existingCenter.setAvailableShifts(updatedCenter.getAvailableShifts());

        if (updatedCenter.getWorkStations() != null) {
            updatedCenter.getWorkStations().forEach(machine -> machine.setWorkCenter(existingCenter));
            existingCenter.setWorkStations(updatedCenter.getWorkStations());
        }

        return workCenterResponseMapper.toDTO(workCenterRepository.save(existingCenter));
    }

    private WorkCenter getActiveWorkCenterById(int id) {
        WorkCenter workCenter = workCenterRepository.findById(id).orElseThrow(() ->
                new ResourceNotFoundException("Work Center does not exist"));

        if (workCenter.getDeletedDate() != null) {
            throw new ResourceNotFoundException("Work Center does not exist");
        }

        return workCenter;
    }

    @Override
    public void deleteWorkCenter(int id) {

        WorkCenter workCenter = getActiveWorkCenterById(id);

        workCenter.setDeletedDate(new Date());
        workCenterRepository.save(workCenter);

    }

    @Override
    public WorkCenterResponseDTO getWorkCenterById(int id) {
        return workCenterResponseMapper.toDTO(getActiveWorkCenterById(id));

    }

    @Override
    public WorkCenter getWorkCenterEntityById(int id) {
        return getActiveWorkCenterById(id);

    }


    @Override
    public WorkCenterResponseDTO getWorkCenterByCode(String centerCode) {
        WorkCenter workCenter = workCenterRepository.findByCenterCode(centerCode)
                .orElseThrow(() -> new ResourceNotFoundException("Work Center does not exist"));

        if (workCenter.getDeletedDate() != null) {
            throw new ResourceNotFoundException("Work Center does not exist");
        }

        return workCenterResponseMapper.toDTO(workCenter);
    }

    @Override
    public List<WorkCenter> getAllWorkCenters() {
        return workCenterRepository.findAll()
                .stream()
                .filter(workCenter -> workCenter.getDeletedDate() == null)
                .collect(Collectors.toList());
    }

    @Override
    public List<WorkCenter> getAllActiveWorkCenters() {
        return workCenterRepository.findAllActive();
    }

    @Override
    public List<WorkCenter> searchByNameOrCode(String query) {
        return workCenterRepository.search(query)
                .stream()
                .filter(workCenter -> workCenter.getDeletedDate() == null)
                .collect(Collectors.toList());
    }

    @Override
    public boolean existsByCenterCode(String centerCode) {
        return workCenterRepository.findByCenterCode(centerCode)
                .map(workCenter -> workCenter.getDeletedDate() == null)
                .orElse(false);
    }

    @Override
    public WorkCenter updateStatus(int id, WorkCenter.WorkCenterStatus status) {
        WorkCenter workCenter = getActiveWorkCenterById(id);
        workCenter.setWorkCenterStatus(status);
        return workCenterRepository.save(workCenter);
    }

    @Override
    public List<WorkCenter> getCentersByStatus(WorkCenter.WorkCenterStatus status) {
        return workCenterRepository.findByWorkCenterStatus(status)
                .stream()
                .filter(workCenter -> workCenter.getDeletedDate() == null)
                .collect(Collectors.toList());
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
                Predicate textMatch = cb.or(centerName, centerCode);
                predicate = cb.and(predicate, textMatch);
            }
            return predicate;
        };

        Page<WorkCenter> workCenters = workCenterRepository.findAll(spec, pageable);
        Page<WorkCenterResponseDTO> workCenterResponseDTOS = workCenters.map(workCenter -> workCenterResponseMapper.toDTO(workCenter));
        return workCenterResponseDTOS;
    }
}
